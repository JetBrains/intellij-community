/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.canBeFinal;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CanBeFinalInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.canBeFinal.CanBeFinalInspection");

  public boolean REPORT_CLASSES;
  public boolean REPORT_METHODS;
  public boolean REPORT_FIELDS = true;
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.can.be.final.display.name");
  @NonNls public static final String SHORT_NAME = "CanBeFinal";
  @NonNls private static final String QUICK_FIX_NAME = InspectionsBundle.message("inspection.can.be.final.accept.quickfix");

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportClassesCheckbox;
    private final JCheckBox myReportMethodsCheckbox;
    private final JCheckBox myReportFieldsCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;


      myReportClassesCheckbox = new JCheckBox(InspectionsBundle.message("inspection.can.be.final.option"));
      myReportClassesCheckbox.setSelected(REPORT_CLASSES);
      myReportClassesCheckbox.getModel().addItemListener(e -> REPORT_CLASSES = myReportClassesCheckbox.isSelected());
      gc.gridy = 0;
      add(myReportClassesCheckbox, gc);

      myReportMethodsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.can.be.final.option1"));
      myReportMethodsCheckbox.setSelected(REPORT_METHODS);
      myReportMethodsCheckbox.getModel().addItemListener(e -> REPORT_METHODS = myReportMethodsCheckbox.isSelected());
      gc.gridy++;
      add(myReportMethodsCheckbox, gc);

      myReportFieldsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.can.be.final.option2"));
      myReportFieldsCheckbox.setSelected(REPORT_FIELDS);
      myReportFieldsCheckbox.getModel().addItemListener(e -> REPORT_FIELDS = myReportFieldsCheckbox.isSelected());

      gc.weighty = 1;
      gc.gridy++;
      add(myReportFieldsCheckbox, gc);
    }
  }

  public boolean isReportClasses() {
    return REPORT_CLASSES;
  }

  public boolean isReportMethods() {
    return REPORT_METHODS;
  }

  public boolean isReportFields() {
    return REPORT_FIELDS;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Override
  @Nullable
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new CanBeFinalAnnotator(refManager);
  }


  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull final RefEntity refEntity,
                                                @NotNull final AnalysisScope scope,
                                                @NotNull final InspectionManager manager,
                                                @NotNull final GlobalInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefJavaElement) {
      final RefJavaElement refElement = (RefJavaElement)refEntity;
      if (refElement instanceof RefParameter) return null;
      if (!refElement.isReferenced()) return null;
      if (refElement.isSyntheticJSP()) return null;
      if (refElement.isFinal()) return null;
      if (!((RefElementImpl)refElement).checkFlag(CanBeFinalAnnotator.CAN_BE_FINAL_MASK)) return null;

      final PsiMember psiMember = (PsiMember)refElement.getElement();
      if (psiMember == null || !CanBeFinalHandler.allowToBeFinal(psiMember)) return null;

      PsiIdentifier psiIdentifier = null;
      if (refElement instanceof RefClass) {
        RefClass refClass = (RefClass)refElement;
        if (refClass.isInterface() || refClass.isAnonymous() || refClass.isAbstract()) return null;
        if (!isReportClasses()) return null;
        psiIdentifier = ((PsiClass)psiMember).getNameIdentifier();
      }
      else if (refElement instanceof RefMethod) {
        RefMethod refMethod = (RefMethod)refElement;
        if (refMethod.getOwnerClass().isFinal()) return null;
        if (!isReportMethods()) return null;
        psiIdentifier = ((PsiMethod)psiMember).getNameIdentifier();
      }
      else if (refElement instanceof RefField) {
        if (!((RefField)refElement).isUsedForWriting()) return null;
        if (!isReportFields()) return null;
        psiIdentifier = ((PsiField)psiMember).getNameIdentifier();
      }


      if (psiIdentifier != null) {
        return new ProblemDescriptor[]{manager.createProblemDescriptor(psiIdentifier, InspectionsBundle.message(
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
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (problemsProcessor.getDescriptions(refEntity) == null) return;
        refEntity.accept(new RefJavaVisitor() {
          @Override public void visitMethod(@NotNull final RefMethod refMethod) {
            if (!refMethod.isStatic() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) &&
                !(refMethod instanceof RefImplicitConstructor)) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalJavaInspectionContext.DerivedMethodsProcessor() {
                @Override
                public boolean process(PsiMethod derivedMethod) {
                  ((RefElementImpl)refMethod).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                  problemsProcessor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          }

          @Override public void visitClass(@NotNull final RefClass refClass) {
            if (!refClass.isAnonymous()) {
              globalContext.enqueueDerivedClassesProcessor(refClass, new GlobalJavaInspectionContext.DerivedClassesProcessor() {
                @Override
                public boolean process(PsiClass inheritor) {
                  ((RefClassImpl)refClass).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                  problemsProcessor.ignoreElement(refClass);
                  return false;
                }
              });
            }
          }

          @Override public void visitField(@NotNull final RefField refField) {
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

      }
    });

    return false;
  }


  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(null);
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

  private static class AcceptSuggested implements LocalQuickFix {
    private final RefManager myManager;

    public AcceptSuggested(final RefManager manager) {
      myManager = manager;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return QUICK_FIX_NAME;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
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
