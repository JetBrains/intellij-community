// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
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
import com.intellij.psi.codeStyle.CodeStyleManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection.isGenericException;

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

      //check read-only status for derived methods
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(refsToDelete)) return;

      WriteAction.run(() -> {
        for (final PsiElement element : refsToDelete) {
          new CommentTracker().deleteAndRestoreComments(element);
        }
      });

    }

    private static void fixTryStatements(final @NotNull PsiMethod method, final CommonProblemDescriptor @NotNull [] problems) {
      final PsiManager psiManager = method.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());

      final Set<PsiClassType> redundantTypes = getRedundantExceptionTypes(method, problems);

      final Set<PsiCatchSection> redundantCatches = getRedundantCatchesOfMethod(method, redundantTypes);

      final Set<PsiCatchSection> catchesOfMultipleCalls = getCatchesOfMultipleCalls(redundantCatches);

      final List<PsiTryStatement> tryStatements = ContainerUtil.map(redundantCatches, PsiCatchSection::getTryStatement);

      if (!FileModificationService.getInstance().preparePsiElementsForWrite(tryStatements)) return;

      WriteAction.run(() -> {
        for (final PsiCatchSection aCatch : redundantCatches) {
          if (catchesOfMultipleCalls.contains(aCatch)) continue;

          final PsiTryStatement tryStatement = aCatch.getTryStatement();
          final PsiType catchType = aCatch.getCatchType();

          if (catchType instanceof PsiDisjunctionType) {
            final PsiTypeElement catchParamType = getCatchTypeElement(aCatch);
            if (catchParamType == null) continue;

            final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)catchType;
            final List<PsiType> neededTypes = ContainerUtil.filter(disjunctionType.getDisjunctions(),
                                                                   typ -> isGenericException(typ) ||
                                                                          !ContainerUtil.exists(redundantTypes, typ::isAssignableFrom)
            );

            if (!neededTypes.isEmpty()) {
              final PsiType newDisjunctionType = PsiDisjunctionType.createDisjunction(neededTypes, psiManager);
              final PsiTypeElement newDisjunctionTypeElement = factory.createTypeElement(newDisjunctionType);
              final PsiElement element = new CommentTracker().replaceAndRestoreComments(catchParamType, newDisjunctionTypeElement);
              JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(element);
            }
            else {
              new CommentTracker().deleteAndRestoreComments(aCatch);
            }
          }
          else {
            new CommentTracker().deleteAndRestoreComments(aCatch);
          }

          if (tryStatement.getCatchSections().length != 0 ||
              tryStatement.getFinallyBlock() != null ||
              tryStatement.getResourceList() != null) {
            CodeStyleManager.getInstance(method.getProject()).reformat(tryStatement);
            continue;
          }

          deleteTryStatement(tryStatement);
        }
      });
    }

    private static void deleteTryStatement(PsiTryStatement tryStatement) {
      final PsiCodeBlock parentCodeBlock = (PsiCodeBlock)tryStatement.getParent();
      final PsiCodeBlock block = tryStatement.getTryBlock();

      if (block == null) return;
      for (PsiStatement statement : block.getStatements()) {
        parentCodeBlock.addBefore(statement, tryStatement);
      }
      new CommentTracker().deleteAndRestoreComments(tryStatement);
    }

    @Nullable
    private static PsiTypeElement getCatchTypeElement(PsiCatchSection aCatch) {
      final PsiParameter catchParam = PsiTreeUtil.getChildOfType(aCatch, PsiParameter.class);
      if (catchParam == null) return null;
      final PsiTypeElement catchParamType = PsiTreeUtil.getChildOfType(catchParam, PsiTypeElement.class);
      if (catchParamType == null) return null;
      return catchParamType;
    }

    @NotNull
    private static Set<PsiCatchSection> getCatchesOfMultipleCalls(Set<PsiCatchSection> redundantCatches) {
      final Set<PsiCatchSection> catchesOfMultipleCalls = new HashSet<>(redundantCatches.size());
      for (final PsiCatchSection aCatch : redundantCatches) {
        final PsiType catchType = aCatch.getCatchType();
        if (catchType == null) continue;

        final PsiTryStatement tryStatement = aCatch.getTryStatement();
        final PsiCodeBlock block = tryStatement.getTryBlock();
        final PsiResourceList resourceList = tryStatement.getResourceList();

        final Set<PsiMethod> methodsOfCatch = getMethodsOfCatch(catchType, block);
        methodsOfCatch.addAll(getMethodsOfCatch(catchType, resourceList));

        // one of the elements is always the method that is currently under the investigation
        if (methodsOfCatch.size() > 1) catchesOfMultipleCalls.add(aCatch);
      }
      return catchesOfMultipleCalls;
    }

    private static Set<PsiClassType> getRedundantExceptionTypes(final @NotNull PsiMethod method, final CommonProblemDescriptor @NotNull [] problems) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
      return StreamEx.of(problems)
        .select(ProblemDescriptor.class)
        .map(ProblemDescriptor::getPsiElement)
        .select(PsiJavaCodeReferenceElement.class)
        .map(factory::createType)
        .toSet();
    }

    @NotNull
    private static Set<PsiCatchSection> getRedundantCatchesOfMethod(@NotNull PsiMethod method, Set<PsiClassType> redundantTypes) {
      final Set<PsiCatchSection> redundantCatches = new HashSet<>();

      final Collection<PsiReference> references = ReferencesSearch.search(method).findAll();
      for (final PsiReference reference : references) {
        if (!(reference instanceof PsiElement)) continue;
        final PsiElement element = (PsiElement)reference;
        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        PsiTreeUtil.treeWalkUp(element, clazz, (curr, prev) -> {
          if (!(curr instanceof PsiTryStatement)) return true;
          final PsiTryStatement tryStatement = (PsiTryStatement)curr;

          final List<PsiCatchSection> catchSections = ContainerUtil.filter(tryStatement.getCatchSections(), catchSection -> {
            final PsiType catchType = catchSection.getCatchType();

            if (catchType == null || isGenericException(catchType)) return false;

            return ContainerUtil.exists(redundantTypes, catchType::isAssignableFrom);
          });

          redundantCatches.addAll(catchSections);

          return true;
        });
      }
      return redundantCatches;
    }

    @NotNull
    private static Set<PsiMethod> getMethodsOfCatch(final @NotNull PsiType catchType, final @Nullable PsiElement block) {
      if (block == null) return Collections.emptySet();

      final Set<PsiMethod> calls = new HashSet<>(1);

      block.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitCallExpression(PsiCallExpression callExpression) {
          final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult(callExpression);
          final PsiElement element = result.getElement();
          final PsiMethod resolvedMethod = element instanceof PsiMethod ? (PsiMethod)element : null;

          final List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(callExpression, block);
          final boolean isHandled = ContainerUtil.exists(exceptionTypes, catchType::isAssignableFrom);
          if (isHandled) calls.add(resolvedMethod);
        }
      });

      return calls;
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
        .filter(throwRefType -> unThrownSet.contains(throwRefType.getType().resolve()))
        .map(ThrowRefType::getType)
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
                                                        @NotNull final List<PsiClassType> redundantThrows) {
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
