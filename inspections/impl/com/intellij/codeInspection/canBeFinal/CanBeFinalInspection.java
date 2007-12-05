/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.canBeFinal;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class CanBeFinalInspection extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.canBeFinal.CanBeFinalInspection");

  public boolean REPORT_CLASSES = false;
  public boolean REPORT_METHODS = false;
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
      myReportClassesCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_CLASSES = myReportClassesCheckbox.isSelected();
        }
      });
      gc.gridy = 0;
      add(myReportClassesCheckbox, gc);

      myReportMethodsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.can.be.final.option1"));
      myReportMethodsCheckbox.setSelected(REPORT_METHODS);
      myReportMethodsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_METHODS = myReportMethodsCheckbox.isSelected();
        }
      });
      gc.gridy++;
      add(myReportMethodsCheckbox, gc);

      myReportFieldsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.can.be.final.option2"));
      myReportFieldsCheckbox.setSelected(REPORT_FIELDS);
      myReportFieldsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_FIELDS = myReportFieldsCheckbox.isSelected();
        }
      });

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

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Nullable
  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return new CanBeFinalAnnotator(refManager);
  }


  @Nullable
  public CommonProblemDescriptor[] checkElement(final RefEntity refEntity,
                                                final AnalysisScope scope,
                                                final InspectionManager manager,
                                                final GlobalInspectionContext globalContext,
                                                final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      if (refElement instanceof RefParameter) return null;
      if (!refElement.isReferenced()) return null;
      if (refElement.isSyntheticJSP()) return null;
      if (refElement.isFinal()) return null;
      if (!((RefElementImpl)refElement).checkFlag(CanBeFinalAnnotator.CAN_BE_FINAL_MASK)) return null;

      PsiIdentifier psiIdentifier = null;
      if (refElement instanceof RefClass) {
        RefClass refClass = (RefClass)refElement;
        if (refClass.isInterface() || refClass.isAnonymous() || refClass.isAbstract()) return null;
        if (!isReportClasses()) return null;
        psiIdentifier = refClass.getElement().getNameIdentifier();
      }
      else if (refElement instanceof RefMethod) {
        RefMethod refMethod = (RefMethod)refElement;
        if (refMethod.getOwnerClass().isFinal()) return null;
        if (!isReportMethods()) return null;
        psiIdentifier = ((PsiMethod)refMethod.getElement()).getNameIdentifier();
      }
      else if (refElement instanceof RefField) {
        if (!isReportFields()) return null;
        psiIdentifier = ((RefField)refElement).getElement().getNameIdentifier();
      }


      if (psiIdentifier != null) {
        return new ProblemDescriptor[]{manager.createProblemDescriptor(psiIdentifier, InspectionsBundle.message(
          "inspection.export.results.can.be.final.description"), new AcceptSuggested(globalContext.getRefManager()),
                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
      }
    }
    return null;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemsProcessor) {
    for (SmartRefElementPointer entryPoint : globalContext.getRefManager().getEntryPointsManager().getEntryPoints()) {
      final RefEntity refElement = entryPoint.getRefElement();
      if (refElement != null) {
        problemsProcessor.ignoreElement(refElement);
      }
    }
    globalContext.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (problemsProcessor.getDescriptions(refEntity) == null) return;
        refEntity.accept(new RefVisitor() {
          @Override public void visitMethod(final RefMethod refMethod) {
            if (!refMethod.isStatic() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) &&
                !(refMethod instanceof RefImplicitConstructor)) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
                  ((RefElementImpl)refMethod).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                  problemsProcessor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          }

          @Override public void visitClass(final RefClass refClass) {
            if (!refClass.isAnonymous()) {
              globalContext.enqueueDerivedClassesProcessor(refClass, new GlobalInspectionContextImpl.DerivedClassesProcessor() {
                public boolean process(PsiClass inheritor) {
                  ((RefClassImpl)refClass).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                  problemsProcessor.ignoreElement(refClass);
                  return false;
                }
              });
            }
          }

          @Override public void visitField(final RefField refField) {
            globalContext.enqueueFieldUsagesProcessor(refField, new GlobalInspectionContextImpl.UsagesProcessor() {
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


  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(null);
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    private RefManager myManager;

    public AcceptSuggested(final RefManager manager) {
      myManager = manager;
    }

    @NotNull
    public String getName() {
      return QUICK_FIX_NAME;
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierListOwner psiElement = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
      if (psiElement != null) {
        RefElement refElement = myManager != null ? myManager.getReference(psiElement) : null;
        try {
          if (psiElement instanceof PsiVariable) {
            ((PsiVariable)psiElement).normalizeDeclaration();
          }
          final PsiModifierList modifierList = psiElement.getModifierList();
          LOG.assertTrue(modifierList != null);
          modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        if (refElement != null) {
          RefUtil.getInstance().setIsFinal(refElement, true);
        }
      }
    }
  }

}
