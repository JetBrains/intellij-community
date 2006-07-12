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
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class CanBeFinalInspection extends FilteringInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.canBeFinal.CanBeFinalInspection");

  public boolean REPORT_CLASSES = true;
  public boolean REPORT_METHODS = true;
  public boolean REPORT_FIELDS = true;
  private QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.can.be.final.display.name");
  @NonNls public static final String SHORT_NAME = "CanBeFinal";
  private CanBeFinalFilter myFilter;
  private CanBeFinalComposer myComposer;

  public CanBeFinalInspection() {
    myQuickFixActions = new QuickFixAction[]{new AcceptSuggested()};
  }

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

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
  }

  public void initialize(GlobalInspectionContextImpl context) {
    super.initialize(context);
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    final CanBeFinalAnnotator annotator = new CanBeFinalAnnotator(refManager);
    refManager.registerGraphAnnotator(annotator);
    annotator.setMask(refManager.getLastUsedMask());
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    final CanBeFinalFilter filter = new CanBeFinalFilter(this);
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefElement)) return;
        if (filter.accepts((RefElement)refEntity)) {
          refEntity.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              if (!refMethod.isStatic() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) &&
                  !(refMethod instanceof RefImplicitConstructor)) {
                getContext().enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                  public boolean process(PsiMethod derivedMethod) {
                    ((RefElementImpl)refMethod).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                    return false;
                  }
                });
              }
            }

            public void visitClass(final RefClass refClass) {
              if (!refClass.isAnonymous()) {
                getContext().enqueueDerivedClassesProcessor(refClass, new GlobalInspectionContextImpl.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    ((RefClassImpl)refClass).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                    return false;
                  }
                });
              }
            }

            public void visitField(final RefField refField) {
              getContext().enqueueFieldUsagesProcessor(refField, new GlobalInspectionContextImpl.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  PsiElement expression = psiReference.getElement();
                  if (expression instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)expression)) {
                    ((RefFieldImpl)refField).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
                    return false;
                  }
                  return true;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new CanBeFinalFilter(this);
    }
    return myFilter;
  }

  protected void resetFilter() {
    myFilter = null;
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new CanBeFinalComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    final CanBeFinalFilter filter = new CanBeFinalFilter(this);

    getRefManager().iterate(new RefVisitor() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefElement)) return;
        if (filter.accepts((RefElement)refEntity)) {
          Element element = XMLExportUtl.createElement(refEntity, parentNode, -1, null);
          Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));

          final HighlightSeverity severity = getCurrentSeverity((RefElement)refEntity);
          final String attributeKey = getTextAttributeKey(severity, null);
          problemClassElement.setAttribute("severity", severity.myName);
          problemClassElement.setAttribute("attribute_key", attributeKey);

          problemClassElement.addContent(InspectionsBundle.message("inspection.export.results.can.be.final"));
          element.addContent(problemClassElement);

          Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
          descriptionElement.addContent(InspectionsBundle.message("inspection.export.results.can.be.final.description"));
          element.addContent(descriptionElement);
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    return myQuickFixActions;
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[]{GlobalInspectionContextImpl.BUILD_GRAPH, GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private static void makeFinal(PsiModifierListOwner psiElement, RefElement refElement) {
    try {
      if (psiElement instanceof PsiVariable) {
        ((PsiVariable)psiElement).normalizeDeclaration();
      }
      psiElement.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    RefUtil.getInstance().setIsFinal(refElement, true);
  }

  private class AcceptSuggested extends QuickFixAction {
    private AcceptSuggested() {
      super(InspectionsBundle.message("inspection.can.be.final.accept.quickfix"), CanBeFinalInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (RefElement refElement : refElements) {
        if (!((RefElementImpl)refElement).checkFlag(CanBeFinalAnnotator.CAN_BE_FINAL_MASK)) continue;
        PsiModifierListOwner psiElement = (PsiModifierListOwner)refElement.getElement();

        if (psiElement == null) continue;
        makeFinal(psiElement, refElement);
      }

      return true;
    }
  }

}
