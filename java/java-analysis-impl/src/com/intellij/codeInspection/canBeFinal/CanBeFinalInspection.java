// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.canBeFinal;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CanBeFinalInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(CanBeFinalInspection.class);

  public boolean REPORT_CLASSES;
  public boolean REPORT_METHODS;
  public boolean REPORT_FIELDS = true;
  @NonNls public static final String SHORT_NAME = "CanBeFinal";

  private boolean isReportClasses() {
    return REPORT_CLASSES;
  }

  private boolean isReportMethods() {
    return REPORT_METHODS;
  }

  private boolean isReportFields() {
    return REPORT_FIELDS;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_CLASSES", JavaAnalysisBundle.message("inspection.can.be.final.option")),
      checkbox("REPORT_METHODS", JavaAnalysisBundle.message("inspection.can.be.final.option1")),
      checkbox("REPORT_FIELDS", JavaAnalysisBundle.message("inspection.can.be.final.option2"))
    );
  }

  @Override
  @Nullable
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new CanBeFinalAnnotator(refManager);
  }


  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull final RefEntity refEntity,
                                                           @NotNull final AnalysisScope scope,
                                                           @NotNull final InspectionManager manager,
                                                           @NotNull final GlobalInspectionContext globalContext,
                                                           @NotNull final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof final RefJavaElement refElement) {
      if (refElement instanceof RefParameter) return null;
      if (!refElement.isReferenced()) return null;
      if (refElement.isSyntheticJSP()) return null;
      if (refElement.isFinal()) return null;
      if (!((RefElementImpl)refElement).checkFlag(CanBeFinalAnnotator.CAN_BE_FINAL_MASK)) return null;

      if (refElement instanceof RefClass refClass) {
        if (!isReportClasses()) return null;
        if (refClass.isInterface() || refClass.isAnonymous() || refClass.isAbstract()) return null;
      }
      else if (refElement instanceof RefMethod refMethod) {
        if (!isReportMethods()) return null;
        RefClass ownerClass = refMethod.getOwnerClass();
        if (ownerClass == null || ownerClass.isFinal()) return null;
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return null;
      }
      else if (refElement instanceof RefField field) {
        if (!isReportFields()) return null;
        if (!field.isUsedForWriting()) return null;
      }
      else {
        return null;
      }

      final PsiMember psiMember = ObjectUtils.tryCast(refElement.getPsiElement(), PsiMember.class);
      if (psiMember == null || !CanBeFinalHandler.allowToBeFinal(psiMember)) return null;
      PsiElement psiIdentifier = ((PsiNameIdentifierOwner)psiMember).getNameIdentifier();
      if (psiIdentifier != null) {
        return new ProblemDescriptor[]{manager.createProblemDescriptor(psiIdentifier, JavaAnalysisBundle.message(
          "inspection.export.results.can.be.final.description"), new AcceptSuggested(globalContext.getRefManager()),
                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)};
      }
    }
    return null;
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor problemsProcessor) {
    for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints(manager)) {
      problemsProcessor.ignoreElement(entryPoint);
    }

    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitMethod(@NotNull final RefMethod refMethod) {
        if (problemsProcessor.getDescriptions(refMethod) == null) return;
        if (!refMethod.isStatic() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) &&
            !(refMethod instanceof RefImplicitConstructor)) {
          globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
            ((RefElementImpl)refMethod).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
            problemsProcessor.ignoreElement(refMethod);
            return false;
          });
        }
      }

      @Override
      public void visitClass(@NotNull final RefClass refClass) {
        if (problemsProcessor.getDescriptions(refClass) == null) return;
        if (!refClass.isAnonymous() && !PsiModifier.PRIVATE.equals(refClass.getAccessModifier())) {
          globalContext.enqueueDerivedClassesProcessor(refClass, inheritor -> {
            ((RefClassImpl)refClass).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
            problemsProcessor.ignoreElement(refClass);
            return false;
          });
        }
      }

      @Override
      public void visitField(@NotNull final RefField refField) {
        if (problemsProcessor.getDescriptions(refField) == null) return;
        if (PsiModifier.PRIVATE.equals(refField.getAccessModifier())) return;
        globalContext.enqueueFieldUsagesProcessor(refField, new GlobalJavaInspectionContext.UsagesProcessor() {
          @Override
          public boolean process(PsiReference psiReference) {
            PsiElement expression = psiReference.getElement();
            if (expression instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)expression)) {
              ((RefFieldImpl)refField).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
              problemsProcessor.ignoreElement(refField);
              return false;
            }
            return true;
          }
        });
      }
    });

    return false;
  }


  @Override
  @Nullable
  public QuickFix<ProblemDescriptor> getQuickFix(final String hint) {
    return new AcceptSuggested(null);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested extends PsiUpdateModCommandQuickFix {
    private final RefManager myManager;

    AcceptSuggested(final RefManager manager) {
      myManager = manager;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.can.be.final.accept.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiModifierListOwner psiElement = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
      if (psiElement != null) {
        RefJavaElement refElement = (RefJavaElement)(myManager != null ? myManager.getReference(psiElement) : null);
        try {
          if (psiElement instanceof PsiVariable) {
            ((PsiVariable)psiElement).normalizeDeclaration();
          }
          final PsiModifierList modifierList = psiElement.getModifierList();
          LOG.assertTrue(modifierList != null);
          modifierList.setModifierProperty(PsiModifier.FINAL, true);
          modifierList.setModifierProperty(PsiModifier.VOLATILE, false);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        if (refElement != null) {
          RefJavaUtil.getInstance().setIsFinal(refElement, true);
        }
      }
    }
  }
}
