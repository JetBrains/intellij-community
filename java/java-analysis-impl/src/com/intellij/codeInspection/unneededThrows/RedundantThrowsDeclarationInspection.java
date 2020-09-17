// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.function.Predicate;
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
          final TryStatementGraph graph = new TryStatementGraph(tryStatement);

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
            if (to == null) {
              new CommentTracker().deleteAndRestoreComments(from);
            }
            else {
              final PsiElement element = new CommentTracker().replaceAndRestoreComments(from, to);
              instance.shortenClassReferences(element);
            }
          }

          for (PsiTryStatement tryStatement : tryStatements) {
            if (tryStatement.getCatchSections().length == 0 &&
                tryStatement.getFinallyBlock() == null &&
                tryStatement.getResourceList() == null) {
              BlockUtils.unwrapTryBlock(tryStatement);
            }
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
     * The class represents a directed acyclic graph of catch sections, exceptions and exceptions inducers
     * in a single try statement.
     * For example, for the following code
     * <pre>
     *   void func1() throws Ex1 {}
     *   void func2() throws Ex2 {}
     *   void func3() throws Ex3, Ex4 {}
     *   try {
     *     func1();
     *     func2();
     *     func3();
     *   } catch (Ex1 | Ex2 | Ex3 e) {
     *   } catch (Ex4 e) {
     *   }
     * </pre>
     * the graph has the following structure:
     *
     * <pre>
     *      CatchSection1   CatchSection2
     *        /   |    \       |
     *      Ex1  Ex2   Ex3    Ex4
     *      /     |      \    /
     *  func1() func2() func3()
     *
     *  </pre>
     *
     *  It looks like a tree where the top-level vertices are {@link PsiCatchSection}s, then
     *  there are vertices for the exceptions the top-level vertices handle. A {@link PsiCatchSection}
     *  can handle a disjoint set of exceptions that is why one {@link PsiCatchSection} can have multiple
     *  children. In the lowest level there are particular expressions that induce exceptions.
     */
    private static final class TryStatementGraph {
      private final @NotNull Map<@NotNull Vertex, @NotNull Set<@NotNull Vertex>> myVertices;

      /**
       * The method adds two vertices into the graph and a connection between them
       * @param from a vertex of the connection's start
       * @param to a vertex of the connection's end
       */
      private void add(final @NotNull Vertex from, final @NotNull Vertex to) {
        final Set<@NotNull Vertex> elements = myVertices.computeIfAbsent(from, k -> new HashSet<>());
        elements.add(to);
        myVertices.putIfAbsent(to, new HashSet<>());
      }

      /**
       * The method returns a stream of {@link CatchVertex} vertices, which are the top-level vertices in the graph.
       * The method is used as an entry point to traverse a graph.
       *
       * @return a stream of {@link CatchVertex}.
       */
      @Contract(pure = true)
      public @NotNull StreamEx<CatchVertex> getCatchVertices() {
        return StreamEx.of(myVertices.keySet()).select(CatchVertex.class);
      }

      /**
       * The method returns adjacent vertices of a vertex
       * @param from a vertex to get adjacent vertices for
       * @return a set of adjacent vertices
       */
      @Contract(pure = true)
      public @NotNull Set<@NotNull Vertex> get(final @NotNull Vertex from) {
        return myVertices.get(from);
      }

      /**
       * Build the graph of catch sections, exceptions and exceptions' inducers for a {@link PsiTryStatement}
       * @param tryStatement a {@link PsiTryStatement} to build a graph for
       * @return a graph of catch sections, exceptions and exceptions' inducers
       */
      @Contract(pure = true)
      private TryStatementGraph(final @NotNull PsiTryStatement tryStatement) {
        myVertices = new HashMap<>();

        connectCatchToCatchTypeVertices(tryStatement);

        final PsiCodeBlock block = tryStatement.getTryBlock();
        final PsiResourceList resourceList = tryStatement.getResourceList();

        connectCatchTypesToInducers(block);
        connectCatchTypesToInducers((PsiElement) resourceList);
        connectCatchTypesToInducers(resourceList);
      }

      /**
       * The method traverses the {@link PsiCatchSection}s of a {@link PsiTryStatement}
       * and connects the catch vertices to catch type vertices.
       * @param tryStatement a try statement to traverse
       */
      private void connectCatchToCatchTypeVertices(final @NotNull PsiTryStatement tryStatement) {
        for (final PsiCatchSection catchSection : tryStatement.getCatchSections()) {
          final CatchVertex catchVertex = new CatchVertex(catchSection);

          final PsiParameter parameter = catchSection.getParameter();
          if (parameter == null) continue;

          final PsiType type = parameter.getType();

          if (type instanceof PsiDisjunctionType) {
            final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
            for (PsiType disjunction : disjunctionType.getDisjunctions()) {
              final CatchTypeVertex catchTypeVertex = new CatchTypeVertex(disjunction);
              add(catchVertex, catchTypeVertex);
            }
          }
          else {
            final CatchTypeVertex exceptionVert = new CatchTypeVertex(type);
            add(catchVertex, exceptionVert);
          }
        }
      }

      /**
       * The method extracts {@link PsiCallExpression}s and {@link PsiThrowStatement}s in block that throws
       * exceptions and connects them to the exceptions vertices in the graph
       *
       * @param block a block of code to analyze
       */
      @Contract(pure = true)
      private void connectCatchTypesToInducers(final @Nullable PsiElement block) {
        if (block == null) return;

        block.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitCallExpression(PsiCallExpression callExpression) {
            final ExceptionInducerVertex exceptionInducerVertex = new ExceptionInducerVertex(callExpression);

            final List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(callExpression, block);
            for (PsiClassType exception : exceptions) {
              final CatchTypeVertex catchTypeVertex = new CatchTypeVertex(exception);
              add(catchTypeVertex, exceptionInducerVertex);
            }
          }

          @Override
          public void visitThrowStatement(PsiThrowStatement statement) {
            final PsiExpression exception = statement.getException();
            if (exception == null) return;

            final PsiType type = exception.getType();
            if (type == null) return;

            add(new CatchTypeVertex(type), new ExceptionInducerVertex(statement));
          }
        });
      }

      /**
       * The method traverses the variables in the {@link PsiResourceList} and connects the
       * exceptions from their <code>close</code> method to the exceptions vertices in the graph
       * @param resourceList a resource list to traverse
       */
      private void connectCatchTypesToInducers(final @Nullable PsiResourceList resourceList) {
        if (resourceList == null) return;

        for (final PsiResourceListElement element : resourceList) {
          final ExceptionInducerVertex resource = new ExceptionInducerVertex(element);
          final List<PsiClassType> exceptions = ExceptionUtil.getCloserExceptions(element);
          for (PsiClassType exception : exceptions) {
            final CatchTypeVertex catchType = new CatchTypeVertex(exception);
            add(catchType, resource);
          }
        }
      }

      /**
       * The method breaks connections between the exceptions' vertices and the vertices of their inducers
       * if an exception is in the redundantTypes set and the inducer is a reference to the method
       * @param redundantTypes a list of redundant exception types
       * @param method a method for which the references should break connections to exceptions
       */
      private void breakConnectionsFromRedundantExceptions(final @NotNull Set<PsiClassType> redundantTypes,
                                                           final @NotNull PsiMethod method) {
        for (final CatchVertex catchVertex : getCatchVertices()) {
          final Set<@NotNull Vertex> catchTypeVertices = get(catchVertex);

          for (final Vertex catchTypeVertex : catchTypeVertices) {
            final CatchTypeVertex typeVertex = (CatchTypeVertex)catchTypeVertex;
            if (redundantTypes.stream().noneMatch(typeVertex.myType::isAssignableFrom)) continue;

            final Iterator<@NotNull Vertex> catchTypeInducers = get(catchTypeVertex).iterator();
            while (catchTypeInducers.hasNext()) {
              final ExceptionInducerVertex next = (ExceptionInducerVertex)catchTypeInducers.next();

              final PsiElement callInducer = next.myElement;
              if (!(callInducer instanceof PsiCall)) continue;

              final JavaResolveResult result = PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)callInducer);
              final PsiElement element = result.getElement();
              final PsiMethod resolvedMethod = element instanceof PsiMethod ? (PsiMethod)element : null;

              if (resolvedMethod == method) catchTypeInducers.remove();
            }
          }
        }
      }

      /**
       * The method traverses the set of exceptions' vertices of a catch
       * and filter out those exceptions that have no inducers, ignoring
       * {@link RuntimeException}s and generic exception like {@link Throwable}s,
       * {@link Exception}s, {@link Error}s.
       *
       * @param section a catch section to analyze exceptions of
       * @return a list of exceptions that have inducers according to the graph.
       */
      @Contract(pure = true)
      private @NotNull List<@NotNull PsiType> getEssentialExceptionsOfCatch(final @NotNull PsiCatchSection section) {
        final Predicate<CatchTypeVertex> isEssential = catchType -> {
          final PsiType type = catchType.myType;
          if (ExceptionUtil.isGeneralExceptionType(type)) return true;
          if (type instanceof PsiClassType && ExceptionUtil.isUncheckedException((PsiClassType)type)) return true;
          return !get(catchType).isEmpty();
        };

        return StreamEx.of(get(new CatchVertex(section)))
          .select(CatchTypeVertex.class)
          .filter(isEssential)
          .map(e -> e.myType)
          .select(PsiType.class)
          .toList();
      }

      private interface Vertex { }
      private static final class CatchVertex implements Vertex {
        private final PsiCatchSection myCatchSection;

        private CatchVertex(@NotNull final PsiCatchSection section) {
          myCatchSection = section;
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) return true;
          if (o == null || getClass() != o.getClass()) return false;
          CatchVertex aCatch = (CatchVertex)o;
          return Objects.equals(myCatchSection, aCatch.myCatchSection);
        }

        @Override
        public int hashCode() {
          return Objects.hash(myCatchSection);
        }

        @Override
        public String toString() {
          return myCatchSection.toString();
        }
      }
      private static final class CatchTypeVertex implements Vertex {
        private final PsiType myType;

        private CatchTypeVertex(final @NotNull PsiType type) {
          myType = type;
        }

        @Override
        public boolean equals(Object o) {
          if (this == o) return true;
          if (o == null || getClass() != o.getClass()) return false;
          CatchTypeVertex type = (CatchTypeVertex)o;
          return Objects.equals(myType, type.myType);
        }

        @Override
        public int hashCode() {
          return Objects.hash(myType);
        }

        @Override
        public String toString() {
          return myType.toString();
        }
      }
      private static final class ExceptionInducerVertex implements Vertex {
        private final PsiElement myElement;

        private ExceptionInducerVertex(PsiElement element) {myElement = element;}


        @Override
        public boolean equals(Object o) {
          if (this == o) return true;
          if (o == null || getClass() != o.getClass()) return false;
          ExceptionInducerVertex element = (ExceptionInducerVertex)o;
          return Objects.equals(myElement, element.myElement);
        }

        @Override
        public int hashCode() {
          return Objects.hash(myElement);
        }

        @Override
        public String toString() {
          return myElement.toString();
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
