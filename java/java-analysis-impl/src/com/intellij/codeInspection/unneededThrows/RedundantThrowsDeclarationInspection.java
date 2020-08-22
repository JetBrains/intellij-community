// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.analysis.AnalysisScope;
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
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

    private StreamEx<PsiElement> removeException(@Nullable final RefMethod refMethod,
                                                 @NotNull final PsiType exceptionType,
                                                 @NotNull final PsiMethod psiMethod) {
      final StreamEx<PsiElement> elements = RedundantThrowsDeclarationLocalInspection.getRedundantThrowsCandidates(psiMethod, myIgnoreEntryPoints)
        .filter(throwRefType -> exceptionType.isAssignableFrom(throwRefType.getType()))
        .map(ThrowRefType::getReference)
        .flatMap(ref -> appendRelatedJavadocThrows(refMethod, psiMethod, ref));

      final Stream<PsiElement> tail;
      if (refMethod != null) {
        tail = refMethod.getDerivedMethods().stream()
          .filter(refDerived -> refDerived.getPsiElement() instanceof PsiMethod)
          .flatMap(refDerived -> removeException(refDerived, exceptionType, (PsiMethod)refDerived.getPsiElement()));

        ProblemDescriptionsProcessor.resolveAllProblemsInElement(myProcessor, refMethod);
      } else {
        final Query<PsiMethod> query = OverridingMethodsSearch.search(psiMethod);
        tail = query.findAll().stream()
          .flatMap(method -> removeException(null, exceptionType, method));
      }
      return elements.append(tail);
    }

    /**
     * The method constructs a {@link StreamEx} or {@link PsiElement} by concatenating
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
