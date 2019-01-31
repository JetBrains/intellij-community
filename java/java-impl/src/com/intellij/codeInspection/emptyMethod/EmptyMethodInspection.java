// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.emptyMethod;

import com.intellij.ToolExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import org.jetbrains.uast.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class EmptyMethodInspection extends GlobalJavaBatchInspectionTool {
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.empty.method.display.name");
  @NonNls private static final String SHORT_NAME = "EmptyMethod";

  public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
  @SuppressWarnings("PublicField")
  public boolean commentsAreContent = false;
  @NonNls private static final String QUICK_FIX_NAME = InspectionsBundle.message("inspection.empty.method.delete.quickfix");
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.emptyMethod.EmptyMethodInspection");

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    if (!(refEntity instanceof RefMethod)) {
      return null;
    }
    final RefMethod refMethod = (RefMethod)refEntity;

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
        message = InspectionsBundle.message("inspection.empty.method.problem.descriptor");
      }
    }
    else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {

      message = InspectionsBundle.message("inspection.empty.method.problem.descriptor1");
    }
    else if (areAllImplementationsEmpty(refMethod) && refSuper == null) {
      if (refMethod.hasBody()) {
        if (refMethod.getDerivedMethods().isEmpty()) {
          if (refMethod.getSuperMethods().isEmpty()) {
            message = InspectionsBundle.message("inspection.empty.method.problem.descriptor2");
          }
        }
        else {
          needToDeleteHierarchy = true;
          message = InspectionsBundle.message("inspection.empty.method.problem.descriptor3");
        }
      }
      else {
        if (!refMethod.getDerivedMethods().isEmpty()) {
          needToDeleteHierarchy = true;
          message = InspectionsBundle.message("inspection.empty.method.problem.descriptor4");
        }
      }
    }

    if (message != null) {
      final ArrayList<LocalQuickFix> fixes = new ArrayList<>();
      fixes.add(getFix(processor, needToDeleteHierarchy));
      if (globalContext instanceof GlobalInspectionContextBase &&
          ((GlobalInspectionContextBase)globalContext).getCurrentProfile().getSingleTool() == null) {
        PsiElement psi = refMethod.getPsiElement();
        if (psi instanceof PsiModifierListOwner) {
          SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)psi, qualifiedName -> {
            fixes.add(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
              QuickFixBundle.message("fix.add.special.annotation.text", qualifiedName),
              QuickFixBundle.message("fix.add.special.annotation.family"),
              EXCLUDE_ANNOS, qualifiedName, psi));
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
    UDeclaration uDeclaration = refMethod.getUastElement();
    if (uDeclaration == null) return null;
    PsiElement psi = uDeclaration.getJavaPsi();
    return psi instanceof PsiModifierListOwner ? (PsiModifierListOwner)psi : null;
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
    for (final Object extension : Extensions.getExtensions(ToolExtensionPoints.EMPTY_METHOD_TOOL)) {
      if (((Condition<RefMethod>) extension).value(refMethod)) {
        return false;
      }
    }

    if (commentsAreContent && PsiTreeUtil.findChildOfType(owner, PsiComment.class) != null) {
      return false;
    }

    return true;
  }

  @Nullable
  private static RefMethod findSuperWithBody(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody()) return refSuper;
    }
    return null;
  }

  private boolean areAllImplementationsEmpty(RefMethod refMethod) {
    if (refMethod.hasBody() && !isBodyEmpty(refMethod)) return false;

    for (RefMethod refDerived : refMethod.getDerivedMethods()) {
      if (!areAllImplementationsEmpty(refDerived)) return false;
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
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext context,
                                                @NotNull final ProblemDescriptionsProcessor descriptionsProcessor) {
     manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && descriptionsProcessor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
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
        }
      }
    });

    return false;
  }


  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!EXCLUDE_ANNOS.isEmpty() || commentsAreContent) {
      super.writeSettings(node);
    }
  }

  private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
    return new DeleteMethodQuickFix(processor, needToDeleteHierarchy);
  }

  @Override
  public String getHint(@NotNull final QuickFix fix) {
    if (fix instanceof DeleteMethodQuickFix) {
      return String.valueOf(((DeleteMethodQuickFix)fix).myNeedToDeleteHierarchy);
    }
    return null;
  }

  @Override
  @Nullable
  public LocalQuickFix getQuickFix(final String hint) {
    return new DeleteMethodIntention(hint);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel listPanel = SpecialAnnotationsUtil
      .createSpecialAnnotationsListControl(EXCLUDE_ANNOS, InspectionsBundle.message("special.annotations.annotations.list"));

    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(new SingleCheckboxOptionsPanel("Comments and javadoc count as content", this, "commentsAreContent"), BorderLayout.NORTH);
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }

  private static class DeleteMethodIntention implements LocalQuickFix {
    private final String myHint;

    DeleteMethodIntention(final String hint) {
      myHint = hint;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return QUICK_FIX_NAME;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
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


  private static class DeleteMethodQuickFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
    private final ProblemDescriptionsProcessor myProcessor;
    private final boolean myNeedToDeleteHierarchy;

    DeleteMethodQuickFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
      myProcessor = processor;
      myNeedToDeleteHierarchy = needToDeleteHierarchy;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return QUICK_FIX_NAME;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
       applyFix(project, new ProblemDescriptor[]{descriptor}, new ArrayList<>(), null);
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
    public void applyFix(@NotNull final Project project,
                         @NotNull final CommonProblemDescriptor[] descriptors,
                         @NotNull final List<PsiElement> psiElementsToIgnore,
                         @Nullable final Runnable refreshViews) {
      for (CommonProblemDescriptor descriptor : descriptors) {
        RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
        if (refElement instanceof RefMethod && refElement.isValid()) {
          RefMethod refMethod = (RefMethod)refElement;
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
}
