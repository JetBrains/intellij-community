// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection.ThrowRefType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RedundantThrowsDeclarationInspection extends GlobalJavaBatchInspectionTool {
  public boolean IGNORE_ENTRY_POINTS = false;

  private final RedundantThrowsDeclarationLocalInspection myLocalInspection = new RedundantThrowsDeclarationLocalInspection(this);

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("ignore.exceptions.thrown.by.entry.points.methods"), this, "IGNORE_ENTRY_POINTS");
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (!(refEntity instanceof RefMethod)) return null;

    final RefMethod refMethod = (RefMethod)refEntity;
    if (refMethod.isSyntheticJSP()) return null;

    if (IGNORE_ENTRY_POINTS && refMethod.isEntry()) return null;

    final PsiClass[] unThrown = refMethod.getUnThrownExceptions();
    if (unThrown == null) return null;

    final PsiElement element = refMethod.getPsiElement();
    if (!(element instanceof PsiMethod)) return null;

    final PsiMethod method = (PsiMethod)element;

    if (method.hasModifier(JvmModifier.NATIVE)) return null;
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass())) return null;

    final Set<PsiClass> unThrownSet = ContainerUtil.set(unThrown);

    return RedundantThrowsDeclarationLocalInspection.getRedundantThrowsCandidates(method, IGNORE_ENTRY_POINTS)
      .filter(throwRefType -> unThrownSet.contains(throwRefType.getType().resolve()))
      .map(throwRefType -> {
        final PsiElement throwsRef = throwRefType.getReference();
        final String message = getMessage(refMethod);
        final MyQuickFix fix = new MyQuickFix(processor, throwRefType.getType().getClassName(), IGNORE_ENTRY_POINTS);
        return manager.createProblemDescriptor(throwsRef, message, fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
      })
      .toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @NotNull
  private static @InspectionMessage String getMessage(@NotNull final RefMethod refMethod) {
    final RefClass ownerClass = refMethod.getOwnerClass();
    if (refMethod.isAbstract() || ownerClass != null && ownerClass.isInterface()) {
      return JavaAnalysisBundle.message("inspection.redundant.throws.problem.descriptor", "<code>#ref</code>");
    }
    if (!refMethod.getDerivedMethods().isEmpty()) {
      return JavaAnalysisBundle.message("inspection.redundant.throws.problem.descriptor1", "<code>#ref</code>");
    }

    return JavaAnalysisBundle.message("inspection.redundant.throws.problem.descriptor2", "<code>#ref</code>");
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
              globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                processor.ignoreElement(refMethod);
                return true;
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public QuickFix<ProblemDescriptor> getQuickFix(String hint) {
    return new MyQuickFix(null, hint, IGNORE_ENTRY_POINTS);
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    return fix instanceof MyQuickFix ? ((MyQuickFix)fix).myHint : null;
  }

  @NotNull
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new RedundantThrowsGraphAnnotator(refManager);
  }

  private static final class MyQuickFix implements LocalQuickFix {
    private final ProblemDescriptionsProcessor myProcessor;
    private final String myHint;
    private final boolean myIgnoreEntryPoints;

    MyQuickFix(final ProblemDescriptionsProcessor processor, final String hint, boolean ignoreEntryPoints) {
      myProcessor = processor;
      myHint = hint;
      myIgnoreEntryPoints = ignoreEntryPoints;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.throws.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiMethod psiMethod;
      final CommonProblemDescriptor[] problems;
      final RefMethod refMethod;

      if (myProcessor != null) {
        final RefEntity refElement = myProcessor.getElement(descriptor);
        if (!(refElement instanceof RefMethod) || !refElement.isValid()) return;

        refMethod = (RefMethod)refElement;
        psiMethod = ObjectUtils.tryCast(refMethod.getPsiElement(), PsiMethod.class);

        problems = myProcessor.getDescriptions(refMethod);
      }
      else {
        psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (psiMethod == null) return;

        refMethod = null;
        problems = new CommonProblemDescriptor[]{descriptor};
      }

      removeExcessiveThrows(refMethod, psiMethod, problems);
    }

    private void removeExcessiveThrows(@Nullable final RefMethod refMethod,
                                       @Nullable final PsiMethod psiMethod,
                                       final CommonProblemDescriptor @Nullable [] problems) {
      if (psiMethod == null) return;
      if (problems == null) return;

      final Project project = psiMethod.getProject();
      final PsiManager psiManager = PsiManager.getInstance(project);

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());

      fixTryStatements(psiMethod, problems);

      final Set<PsiElement> refsToDelete = Arrays.stream(problems)
        .map(problem -> (ProblemDescriptor)problem)
        .map(ProblemDescriptor::getPsiElement)
        .filter(psiElement -> psiElement instanceof PsiJavaCodeReferenceElement)
        .map(reference -> (PsiJavaCodeReferenceElement)reference)
        .map(factory::createType)
        .flatMap(psiClassType -> removeException(refMethod, psiClassType, psiMethod))
        .collect(Collectors.toSet());

      if (!FileModificationService.getInstance().preparePsiElementsForWrite(refsToDelete)) return;

      WriteAction.run(() -> {
        for (final PsiElement element : refsToDelete) {
          new CommentTracker().deleteAndRestoreComments(element);
        }
      });

    }

    private static void fixTryStatements(final @NotNull PsiMethod method, final CommonProblemDescriptor @NotNull [] problems) {
      final Project project = method.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

      final Set<PsiClassType> redundantTypes = getRedundantExceptionTypes(problems, factory);

      final Map<@NotNull PsiFile, @NotNull Set<@NotNull PsiTryStatement>> tryStatementsInFile = getFilesWithTryStatements(method);

      for (Set<PsiTryStatement> tryStatements : tryStatementsInFile.values()) {
        final Map<@NotNull PsiElement, @Nullable PsiElement> mappings = new HashMap<>();

        for (final PsiTryStatement tryStatement : tryStatements) {
          final TryStatementInfo graph = new TryStatementInfo(tryStatement);

          graph.breakConnectionsFromRedundantExceptions(redundantTypes, method);

          for (final PsiCatchSection section : tryStatement.getCatchSections()) {
            final PsiParameter parameter = section.getParameter();
            if (parameter == null) continue;

            final PsiTypeElement catchParamTypeElement = parameter.getTypeElement();
            if (catchParamTypeElement == null) continue;

            final PsiType catchParamType = parameter.getType();

            final List<PsiType> types = graph.getEssentialExceptionsOfCatch(section);

            if (types.isEmpty()) {
              mappings.put(section, null);
            }
            else if (catchParamType instanceof PsiDisjunctionType) {
              final PsiDisjunctionType parameterType = (PsiDisjunctionType)catchParamType;
              if (parameterType.getDisjunctions().size() == types.size()) continue;
              final PsiType newDisjunctionType = PsiDisjunctionType.createDisjunction(types, method.getManager());

              final PsiTypeElement newDisjunctionTypeElement = factory.createTypeElement(newDisjunctionType);

              mappings.put(catchParamTypeElement, newDisjunctionTypeElement);
            }
          }
        }

        if (mappings.isEmpty()) continue;

        if (!FileModificationService.getInstance().preparePsiElementsForWrite(tryStatements)) return;
        WriteAction.run(() -> {
          final JavaCodeStyleManager instance = JavaCodeStyleManager.getInstance(project);
          for (Map.Entry<PsiElement, PsiElement> mapping : mappings.entrySet()) {
            final PsiElement from = mapping.getKey();
            final PsiElement to = mapping.getValue();
            if (!from.isValid()) continue;
            if (to == null) {
              new CommentTracker().deleteAndRestoreComments(from);
            }
            else if (to.isValid()) {
              final PsiElement element = new CommentTracker().replaceAndRestoreComments(from, to);
              instance.shortenClassReferences(element);
            }
          }

          for (PsiTryStatement tryStatement : tryStatements) {
            if (!tryStatement.isValid()) continue;
            if (tryStatement.getCatchSections().length != 0) continue;
            if (tryStatement.getFinallyBlock() != null) continue;
            if (tryStatement.getResourceList() != null) continue;

            BlockUtils.unwrapTryBlock(tryStatement);
          }
        });
      }
    }

    /**
     * The method walks through all the references to the method and looks for enclosing {@link PsiTryStatement}s.
     * The resulting map contains pairs &lt;{@link PsiFile}, {@link Set}&lt;{@link PsiTryStatement}&gt;&gt;
     * which represents a {@link PsiFile} and a set of all the found {@link PsiTryStatement}s in the {@link PsiFile}
     * @param method a method to get references for
     * @return a map of pairs with files and sets of try statements that enclose a reference to the method in the files.
     */
    @Contract(pure = true)
    private static @NotNull Map<@NotNull PsiFile, @NotNull Set<@NotNull PsiTryStatement>> getFilesWithTryStatements(@NotNull PsiMethod method) {
      final Collection<PsiReference> references = ReferencesSearch.search(method).findAll();

      final Map<@NotNull PsiFile, @NotNull Set<@NotNull PsiTryStatement>> tryStatementsInFile = new HashMap<>();

      for (final PsiReference reference : references) {
        if (!(reference instanceof PsiElement)) continue;

        final PsiElement element = (PsiElement)reference;
        final PsiFile file = element.getContainingFile();

        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        PsiTreeUtil.treeWalkUp(element, clazz, (curr, prev) -> {
          if (curr instanceof PsiTryStatement) {
            final Set<PsiTryStatement> tryStatements = tryStatementsInFile.computeIfAbsent(file, k -> new HashSet<>());
            tryStatements.add((PsiTryStatement)curr);
          }
          return true;
        });
      }
      return tryStatementsInFile;
    }


    /**
     * The method extracts references from the set of problems and converts them to exceptions types
     *
     * @param problems initially registered problems
     * @param factory a factory to build types
     * @return a set of exception types
     */
    private static Set<PsiClassType> getRedundantExceptionTypes(final CommonProblemDescriptor @NotNull [] problems,
                                                                final @NotNull PsiElementFactory factory) {
      return StreamEx.of(problems)
        .select(ProblemDescriptor.class)
        .map(ProblemDescriptor::getPsiElement)
        .select(PsiJavaCodeReferenceElement.class)
        .map(factory::createType)
        .toSet();
    }

    /**
     * This class represents relations among catch sections, catch types and exception inducers in a {@link PsiTryStatement}.
     * <p>
     * It contains two maps:
     * <ol>
     *   <li>{@link RedundantThrowsDeclarationInspection.MyQuickFix.TryStatementInfo#catchToExceptionTypes}
     *   contains relations between a catch section and exception types the section handles</li>
     *   <li>{@link RedundantThrowsDeclarationInspection.MyQuickFix.TryStatementInfo#exceptionToInducers}
     *   contains relations between an exception type and places in the code that induce the exception</li>
     * </ol>
     * </p>
     */
    private static final class TryStatementInfo {
      private final @NotNull Map<@NotNull PsiCatchSection, @NotNull Set<@NotNull PsiType>> catchToExceptionTypes;
      private final @NotNull Map<@NotNull PsiType, @NotNull Set<@NotNull PsiElement>> exceptionToInducers;

      private TryStatementInfo(final @NotNull PsiTryStatement tryStatement) {
        catchToExceptionTypes = new HashMap<>();
        exceptionToInducers = new HashMap<>();
        analyzeCatchSections(tryStatement);

        final PsiCodeBlock block = tryStatement.getTryBlock();
        final PsiResourceList resourceList = tryStatement.getResourceList();
        analyzeCodeBlock(block);
        analyzeCodeBlock((PsiElement)resourceList);
        analyzeCodeBlock(resourceList);
      }

      /**
       * The method returns a set of {@link PsiElement}s that induce a {@link PsiType}
       * @param exceptionType a type of an exception for which to get a set of {@link PsiElement} that induce the exception
       * @return a set of {@link PsiElement}s that induce the exception or {@link Collections#emptySet}
       * if no inducers for the exception found
       */
      @Contract(pure = true)
      private @NotNull Set<@NotNull PsiElement> getExceptionInducers(final @NotNull PsiType exceptionType) {
        return exceptionToInducers.getOrDefault(exceptionType, Collections.emptySet());
      }

      /**
       * The method traverses the set of exceptions of a catch and filters out those exceptions that have no inducers,
       * ignoring {@link RuntimeException}s and generic exception like
       * {@link Throwable}s, {@link Exception}s, {@link Error}s.
       *
       * @param catchSection a catch section to analyze exceptions of
       * @return a list of exceptions that have inducers
       */
      @Contract(pure = true)
      private @NotNull List<@NotNull PsiType> getEssentialExceptionsOfCatch(final @NotNull PsiCatchSection catchSection) {
        return ContainerUtil.filter(catchToExceptionTypes.get(catchSection), this::isEssentialException);
      }

      @Contract(pure = true)
      private boolean isEssentialException(final @NotNull PsiType type) {
        if (ExceptionUtil.isGeneralExceptionType(type)) return true;
        if (type instanceof PsiClassType && ExceptionUtil.isUncheckedException((PsiClassType)type)) return true;
        return !getExceptionInducers(type).isEmpty();
      }

      /**
       * This method extracts {@link PsiCatchSection}s from a {@link PsiTryStatement} and adds them and the exception types
       * they handle into {@link RedundantThrowsDeclarationInspection.MyQuickFix.TryStatementInfo#catchToExceptionTypes},
       * so it is easy to have access to exceptions a particular catch statement handles.
       *
       * @param tryStatement a {@link PsiTryStatement} to analyze
       */
      @Contract(pure = true)
      private void analyzeCatchSections(final @NotNull PsiTryStatement tryStatement) {
        for (final PsiCatchSection catchSection : tryStatement.getCatchSections()) {
          final PsiParameter parameter = catchSection.getParameter();
          if (parameter == null) continue;

          final PsiType catchType = parameter.getType();

          if (catchType instanceof PsiDisjunctionType) {
            final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)catchType;
            for (final PsiType disjunction : disjunctionType.getDisjunctions()) {
              addCatchSectionType(catchSection, disjunction);
            }
          }
          else {
            addCatchSectionType(catchSection, catchType);
          }
        }
      }

      /**
       * This method walks through a code block and deduces which expressions throw which exceptions.
       * Such information is stored into {@link RedundantThrowsDeclarationInspection.MyQuickFix.TryStatementInfo#exceptionToInducers},
       * so it becomes easy to get all the places in the code that throw a particular exception.
       *
       * @param block a block of code to analyze
       */
      @Contract(pure = true)
      private void analyzeCodeBlock(final @Nullable PsiElement block) {
        if (block == null) return;

        block.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitCallExpression(PsiCallExpression callExpression) {
            final List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(callExpression, block);
            for (PsiClassType exception : exceptions) {
              addExceptionInducer(exception, callExpression);
            }
          }

          @Override
          public void visitThrowStatement(PsiThrowStatement statement) {
            final PsiExpression exception = statement.getException();
            if (exception == null) return;

            final PsiType type = exception.getType();
            if (type == null) return;

            addExceptionInducer(type, statement);
          }
        });
      }

      /**
       * This method does the same job as {@link RedundantThrowsDeclarationInspection.MyQuickFix.TryStatementInfo#analyzeCodeBlock(PsiElement)},
       * but it instead of places in code it analyzes variables from a {@link PsiResourceList}
       * and checks what exceptions the <code>#close()</code> method of a variable throws.
       *
       * @param resourceList a resourceList from <code>try-with-resources</code> to analyze
       */
      @Contract(pure = true)
      private void analyzeCodeBlock(final @Nullable PsiResourceList resourceList) {
        if (resourceList == null) return;

        for (final PsiResourceListElement element : resourceList) {
          final List<PsiClassType> exceptions = ExceptionUtil.getCloserExceptions(element);
          for (PsiClassType exception : exceptions) {
            addExceptionInducer(exception, element);
          }
        }
      }

      /**
       * The method adds a new link between a catch section and an exception type
       * @param catchSection a catch section to add an exception to
       * @param exception an exception to add to the catch section
       */
      private void addCatchSectionType(final @NotNull PsiCatchSection catchSection, final @NotNull PsiType exception) {
        final Set<@NotNull PsiType> exceptionTypes = catchToExceptionTypes.computeIfAbsent(catchSection, k -> new LinkedHashSet<>());
        exceptionTypes.add(exception);
      }

      /**
       * This method adds a new link between an exception and a place in the code (inducer) that induces the exception
       * @param exception exception type
       * @param inducer a place in the code that induces the exception
       */
      private void addExceptionInducer(final @NotNull PsiType exception, final @NotNull PsiElement inducer) {
        final Set<@NotNull PsiElement> inducers = exceptionToInducers.computeIfAbsent(exception, k -> new HashSet<>());
        inducers.add(inducer);
      }

      /**
       * The method breaks connections between exceptions and the their inducers
       * for exceptions that are in the redundantTypes set and the inducer is a reference to the method
       * @param redundantTypes a list of redundant exception types
       * @param method a method for which the references should break connections to exceptions
       */
      private void breakConnectionsFromRedundantExceptions(final @NotNull Set<? extends PsiClassType> redundantTypes,
                                                           final @NotNull PsiMethod method) {
        for (final @NotNull PsiCatchSection catchSection : catchToExceptionTypes.keySet()) {
          for (final @NotNull PsiType exception : catchToExceptionTypes.get(catchSection)) {
            if (!ContainerUtil.exists(redundantTypes, exception::isAssignableFrom)) continue;

            final Iterator<@NotNull PsiElement> catchTypeInducers = getExceptionInducers(exception).iterator();
            while (catchTypeInducers.hasNext()) {
              final @NotNull PsiElement callInducer = catchTypeInducers.next();

              if (!(callInducer instanceof PsiCall)) continue;

              final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)callInducer);
              final PsiElement element = result.getElement();
              final PsiMethod resolvedMethod = element instanceof PsiMethod ? (PsiMethod)element : null;

              if (resolvedMethod == method) catchTypeInducers.remove();
            }
          }
        }
      }
    }

    private StreamEx<PsiElement> removeException(@Nullable final RefMethod refMethod,
                                                 @NotNull final PsiType exceptionType,
                                                 @NotNull final PsiMethod psiMethod) {
      final StreamEx<PsiElement> elements = RedundantThrowsDeclarationLocalInspection.getRedundantThrowsCandidates(psiMethod, myIgnoreEntryPoints)
        .filter(throwRefType -> exceptionType.isAssignableFrom(throwRefType.getType()))
        .map(ThrowRefType::getReference)
        .flatMap(ref -> appendRelatedJavadocThrows(refMethod, psiMethod, ref));

      if (refMethod != null) {
        ProblemDescriptionsProcessor.resolveAllProblemsInElement(myProcessor, refMethod);
      }
      return elements;
    }

    /**
     * The method constructs a {@link StreamEx} of {@link PsiElement} by concatenating
     * a singleton {@link Stream} that contains the current throws list's element and the related javadoc.
     * Related javadoc are the ones that can be assigned to any of the redundant throws list elements
     *
     * @param refMethod a node in the reference graph corresponding to the Java method.
     * @param psiMethod an instance of {@link PsiMethod} the current throws list's element is related to
     * @param ref the current throws list's element to append related @throws tags to
     * @return a {@link StreamEx} that contains both the current throws list's element and its related javadoc @throws tags
     */
    private StreamEx<PsiElement> appendRelatedJavadocThrows(@Nullable final RefMethod refMethod,
                                                            @NotNull final PsiMethod psiMethod,
                                                            @NotNull final PsiJavaCodeReferenceElement ref) {
      final StreamEx<PsiElement> res = StreamEx.of(ref);
      if (refMethod == null) return res;

      final PsiDocComment comment = psiMethod.getDocComment();
      if (comment == null) return res;

      final PsiClass[] unThrown = refMethod.getUnThrownExceptions();
      if (unThrown == null) return res;

      final Set<PsiClass> unThrownSet = ContainerUtil.set(unThrown);

      final List<PsiClassType> redundantThrows = RedundantThrowsDeclarationLocalInspection.getRedundantThrowsCandidates(psiMethod, myIgnoreEntryPoints)
        .map(ThrowRefType::getType)
        .filter(type -> unThrownSet.contains(type.resolve()))
        .toList();

      final StreamEx<PsiDocTag> javadocThrows = StreamEx.of(comment.getTags())
        .filterBy(PsiDocTag::getName, "throws");

      // if there is only one element in the throws list and there is one redundant throws element,
      // it must be the same element, so return all the @throws tags that are in the javadoc
      final PsiJavaCodeReferenceElement[] throwsListElements = psiMethod.getThrowsList().getReferenceElements();
      if (throwsListElements.length == 1 && redundantThrows.size() == 1) {
        return res.append(javadocThrows);
      }

      final StreamEx<PsiDocTag> relatedJavadocThrows = javadocThrows
        .filter(tag -> isTagRelatedToRedundantThrow(tag, redundantThrows));

      return res.append(relatedJavadocThrows);
    }

    /**
     * A @throws tag is considered related to an element of redundant throws declarations
     * if it can be assigned to the element.
     * @param tag the @throws tag in the javadoc
     * @param redundantThrows the list of redundant throws declarations in the throws list of a method
     * @return true if there is at least one element in the list of redundant throws that can be assigned with the class of the @throws tag,
     * false otherwise
     */
    private static boolean isTagRelatedToRedundantThrow(@NotNull final PsiDocTag tag,
                                                        final @NotNull List<? extends PsiClassType> redundantThrows) {
      assert "throws".equals(tag.getName()) : "the tag has to be of the @throws kind";

      final PsiClass throwsClass = JavaDocUtil.resolveClassInTagValue(tag.getValueElement());
      if (throwsClass == null) return false;
      final PsiClassType type = PsiTypesUtil.getClassType(throwsClass);

      return redundantThrows.stream()
        .anyMatch(e -> e.isAssignableFrom(type));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @NotNull
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return myLocalInspection;
  }
}
