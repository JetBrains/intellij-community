// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.emptyMethod;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class EmptyMethodInspection extends GlobalJavaBatchInspectionTool {
  private static final @NonNls String SHORT_NAME = "EmptyMethod";

  private static final ExtensionPointName<Condition<RefMethod>> CAN_BE_EMPTY_EP = new ExtensionPointName<>("com.intellij.canBeEmpty");

  public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
  @SuppressWarnings("PublicField")
  public boolean commentsAreContent = false;
  private static final Logger LOG = Logger.getInstance(EmptyMethodInspection.class);

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (!(refEntity instanceof RefMethod refMethod)) {
      return null;
    }

    if (!isBodyEmpty(refMethod)) return null;
    if (refMethod.isConstructor()) return null;
    if (refMethod.isSyntheticJSP()) return null;

    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (checkElement(refSuper, scope, manager, globalContext, processor) != null) return null;
    }

    String message = null;
    boolean needToDeleteHierarchy = false;
    RefMethod refSuper = findSuperWithBody(refMethod);
    if (refMethod.isOnlyCallsSuper() && !refMethod.isFinal()) {
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      if (refSuper != null && Comparing.strEqual(refMethod.getAccessModifier(), refSuper.getAccessModifier())){
        if (Comparing.strEqual(refSuper.getAccessModifier(), PsiModifier.PROTECTED) //protected modificator gives access to method in another package
            && !Comparing.strEqual(refUtil.getPackageName(refSuper), refUtil.getPackageName(refMethod))) return null;
        PsiModifierListOwner javaPsi = getAsJavaPsi(refMethod);
        if (javaPsi != null) {
          final PsiModifierList list = javaPsi.getModifierList();
          if (list != null) {
            final PsiModifierListOwner supMethod = getAsJavaPsi(refSuper);
            if (supMethod != null) {
              final PsiModifierList superModifiedList = supMethod.getModifierList();
              LOG.assertTrue(superModifiedList != null);
              if (list.hasModifierProperty(PsiModifier.SYNCHRONIZED) && !superModifiedList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return null;
              }
            }
          }
        }
      }
      if (refSuper == null || refUtil.compareAccess(refMethod.getAccessModifier(), refSuper.getAccessModifier()) <= 0) {
        message = JavaBundle.message("inspection.empty.method.problem.descriptor");
      }
    }
    else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {
      message = JavaBundle.message("inspection.empty.method.problem.descriptor1");
    }
    else if (refSuper == null && areAllImplementationsEmpty(refMethod)) {
      if (refMethod.hasBody()) {
        if (refMethod.getDerivedMethods().isEmpty()) {
          if (refMethod.getSuperMethods().isEmpty()) {
            message = JavaBundle.message("inspection.empty.method.problem.descriptor2");
          }
        }
        else {
          needToDeleteHierarchy = true;
          message = JavaBundle.message("inspection.empty.method.problem.descriptor3");
        }
      }
      else {
        if (!refMethod.getDerivedReferences().isEmpty()) {
          needToDeleteHierarchy = true;
          message = JavaBundle.message("inspection.empty.method.problem.descriptor4");
        }
      }
    }

    if (message != null) {
      final ArrayList<LocalQuickFix> fixes = new ArrayList<>();
      fixes.add(getFix(processor, needToDeleteHierarchy));
      if (globalContext instanceof GlobalInspectionContextBase globalInspectionContextBase &&
          globalInspectionContextBase.getCurrentProfile().getSingleTool() == null) {
        PsiElement psi = refMethod.getPsiElement();
        if (psi instanceof PsiModifierListOwner owner) {
          SpecialAnnotationsUtilBase.processUnknownAnnotations(owner, qualifiedName -> {
            fixes.add(new AddToInspectionOptionListFix<>(
              this, 
              QuickFixBundle.message("fix.add.special.annotation.text", qualifiedName),
              qualifiedName, insp -> insp.EXCLUDE_ANNOS));
            return true;
          });
        }
      }

      PsiElement anchor = UDeclarationKt.getAnchorPsi(refMethod.getUastElement());
      if (anchor != null) {
        final ProblemDescriptor descriptor = manager.createProblemDescriptor(anchor, message, false,
                                                                             fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        return new ProblemDescriptor[]{descriptor};
      }
    }

    return null;
  }

  private static PsiModifierListOwner getAsJavaPsi(RefMethod refMethod) {
    UMethod uMethod = refMethod.getUastElement();
    if (uMethod == null) return null;
    return uMethod.getJavaPsi();
  }

  private boolean isBodyEmpty(final RefMethod refMethod) {
    if (!refMethod.isBodyEmpty()) {
      return false;
    }
    final PsiModifierListOwner owner = getAsJavaPsi(refMethod);
    if (owner == null) {
      return false;
    }
    if (AnnotationUtil.isAnnotated(owner, EXCLUDE_ANNOS, 0)) {
      return false;
    }

    if (CAN_BE_EMPTY_EP.findFirstSafe(condition -> condition.value(refMethod)) != null) {
      return false;
    }

    if (commentsAreContent && PsiTreeUtil.findChildOfType(owner, PsiComment.class) != null) {
      return false;
    }

    return true;
  }

  private static @Nullable RefMethod findSuperWithBody(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody()) return refSuper;
    }
    return null;
  }

  private boolean areAllImplementationsEmpty(@NotNull RefOverridable reference) {
    if (reference instanceof RefMethod) {
      if (((RefMethod)reference).hasBody() && !isBodyEmpty((RefMethod)reference)) return false;
    }
    else if (reference instanceof RefFunctionalExpression) {
      if (!((RefFunctionalExpression)reference).hasEmptyBody()) return false;
    }

    for (RefOverridable derivedReference : reference.getDerivedReferences()) {
      if (!areAllImplementationsEmpty(derivedReference)) return false;
    }

    return true;
  }

  private boolean hasEmptySuperImplementation(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody() && isBodyEmpty(refSuper)) return true;
    }

    return false;
  }

  @Override
  protected boolean queryExternalUsagesRequests(final @NotNull RefManager manager,
                                                final @NotNull GlobalJavaInspectionContext context,
                                                final @NotNull ProblemDescriptionsProcessor descriptionsProcessor) {
     manager.iterate(new RefJavaVisitor() {
       @Override
       public void visitMethod(final @NotNull RefMethod refMethod) {
         if (descriptionsProcessor.getDescriptions(refMethod) == null) return;
         if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
         context.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
           UMethod uDerivedMethod = UastContextKt.toUElement(derivedMethod, UMethod.class);
           if (uDerivedMethod == null) return true;
           UExpression body = uDerivedMethod.getUastBody();
           if (RefMethodImpl.isEmptyExpression(body)) return true;
           if (RefJavaUtil.getInstance().isMethodOnlyCallsSuper(uDerivedMethod)) return true;
           descriptionsProcessor.ignoreElement(refMethod);
           return false;
         });
       }
    });

    return false;
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!EXCLUDE_ANNOS.isEmpty() || commentsAreContent) {
      super.writeSettings(node);
    }
  }

  private static LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
    return new DeleteMethodQuickFix(processor, needToDeleteHierarchy);
  }

  @Override
  public String getHint(final @NotNull QuickFix fix) {
    if (fix instanceof DeleteMethodQuickFix) {
      return String.valueOf(((DeleteMethodQuickFix)fix).myNeedToDeleteHierarchy);
    }
    return null;
  }

  @Override
  public @Nullable LocalQuickFix getQuickFix(final String hint) {
    return new DeleteMethodIntention(hint);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("commentsAreContent", JavaBundle.message("checkbox.comments.and.javadoc.count.as.content")),
      stringList("EXCLUDE_ANNOS", JavaBundle.message("special.annotations.annotations.list"),
                 new JavaClassValidator().annotationsOnly()));
  }

  private static class DeleteMethodIntention implements LocalQuickFix {
    private final String myHint;

    DeleteMethodIntention(final String hint) {
      myHint = hint;
    }

    @Override
    public @NotNull String getFamilyName() {
      return getQuickFixName();
    }

    @Override
    public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class, false);
      if (psiMethod != null) {
        final List<PsiElement> psiElements = new ArrayList<>();
        psiElements.add(psiMethod);
        if (Boolean.valueOf(myHint).booleanValue()) {
          final Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
          query.forEach(pair -> {
            if (pair.first == psiMethod) {
              psiElements.add(pair.second);
            }
            return true;
          });
        }

        ApplicationManager.getApplication().invokeLater(() -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElements), false), project.getDisposed());
      }
    }
  }

  private static final class DeleteMethodQuickFix implements LocalQuickFix, BatchQuickFix {
    // QuickFix is registered for global inspection only; not displayed in the editor anyway
    @SuppressWarnings("ActionIsNotPreviewFriendly")
    private final ProblemDescriptionsProcessor myProcessor;
    private final boolean myNeedToDeleteHierarchy;

    private DeleteMethodQuickFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
      myProcessor = processor;
      myNeedToDeleteHierarchy = needToDeleteHierarchy;
    }

    @Override
    public @NotNull String getFamilyName() {
      return getQuickFixName();
    }

    @Override
    public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
       applyFix(project, new ProblemDescriptor[]{descriptor}, List.of(), null);
    }

    private static void deleteHierarchy(RefMethod refMethod, List<? super PsiElement> result) {
      Collection<RefMethod> derivedMethods = refMethod.getDerivedMethods();
      RefMethod[] refMethods = derivedMethods.toArray(new RefMethod[0]);
      for (RefMethod refDerived : refMethods) {
        deleteMethod(refDerived, result);
      }
      deleteMethod(refMethod, result);
    }

    private static void deleteMethod(RefMethod refMethod, List<? super PsiElement> result) {
      PsiElement psiElement = refMethod.getPsiElement();
      if (psiElement == null) return;
      if (!result.contains(psiElement)) result.add(psiElement);
    }

    @Override
    public void applyFix(final @NotNull Project project,
                         final CommonProblemDescriptor @NotNull [] descriptors,
                         final @NotNull List<PsiElement> psiElementsToIgnore,
                         final @Nullable Runnable refreshViews) {
      for (CommonProblemDescriptor descriptor : descriptors) {
        RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
        if (refElement instanceof RefMethod refMethod && refElement.isValid()) {
          if (myNeedToDeleteHierarchy) {
            deleteHierarchy(refMethod, psiElementsToIgnore);
          }
          else {
            deleteMethod(refMethod, psiElementsToIgnore);
          }
        }
      }
      ApplicationManager.getApplication().invokeLater(() -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElementsToIgnore), false, refreshViews), project.getDisposed());
    }
  }

  private static @IntentionFamilyName String getQuickFixName() {
    return JavaBundle.message("inspection.empty.method.delete.quickfix");
  }
}
