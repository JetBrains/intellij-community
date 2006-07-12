/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2001
 * Time: 8:46:41 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.visibility;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.javaee.ejb.EjbModuleUtil;
import com.intellij.javaee.model.common.ejb.EjbRootElement;
import com.intellij.javaee.model.common.ejb.EntityBean;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class VisibilityInspection extends FilteringInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.visibility.VisibilityInspection");
  public boolean SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
  public boolean SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
  public boolean SUGGEST_PRIVATE_FOR_INNERS = false;
  private WeakerAccessFilter myFilter;
  private QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.visibility.display.name");
  private VisibilityPageComposer myComposer;
  @NonNls public static final String SHORT_NAME = "WeakerAccess";

  public VisibilityInspection() {
    myQuickFixActions = new QuickFixAction[]{new AcceptSuggestedAccess()};
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myPackageLocalForMembersCheckbox;
    private final JCheckBox myPrivateForInnersCheckbox;
    private JCheckBox myPackageLocalForTopClassesCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1;
      gc.weighty = 0;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myPackageLocalForMembersCheckbox = new JCheckBox(InspectionsBundle.message("inspection.visibility.option"));
      myPackageLocalForMembersCheckbox.setSelected(SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS);
      myPackageLocalForMembersCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = myPackageLocalForMembersCheckbox.isSelected();
        }
      });

      gc.gridy = 0;
      add(myPackageLocalForMembersCheckbox, gc);

      myPackageLocalForTopClassesCheckbox = new JCheckBox(InspectionsBundle.message("inspection.visibility.option1"));
      myPackageLocalForTopClassesCheckbox.setSelected(SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES);
      myPackageLocalForTopClassesCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = myPackageLocalForTopClassesCheckbox.isSelected();
        }
      });

      gc.gridy = 1;
      add(myPackageLocalForTopClassesCheckbox, gc);


      myPrivateForInnersCheckbox = new JCheckBox(InspectionsBundle.message("inspection.visibility.option2"));
      myPrivateForInnersCheckbox.setSelected(SUGGEST_PRIVATE_FOR_INNERS);
      myPrivateForInnersCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_PRIVATE_FOR_INNERS = myPrivateForInnersCheckbox.isSelected();
        }
      });

      gc.gridy = 2;
      gc.weighty = 1;
      add(myPrivateForInnersCheckbox, gc);
    }
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
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

  public WeakerAccessFilter getFilter() {
    if (myFilter == null){
      myFilter = new WeakerAccessFilter(this);

    }
    return myFilter;
  }

  protected void resetFilter() {
    myFilter = null;
  }

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    for (SmartRefElementPointer entryPoint : EntryPointsManager.getInstance(getContext().getProject()).getEntryPoints()) {
      RefElement refElement = entryPoint.getRefElement();
      if (refElement != null) {
        getFilter().addIgnoreList(refElement);
      }
    }

    final EjbRootElement[] newEjbModels = EjbModuleUtil.getEjbModels(getContext().getProject());
    for (final EjbRootElement ejbRootElement : newEjbModels) {
      for (final EntityBean entityBean : ejbRootElement.getEnterpriseBeans().getEntities()) {
        PsiClass primaryKeyClass = entityBean.getPrimKeyClass().getValue();
        if (primaryKeyClass != null) {
          for (PsiField field : primaryKeyClass.getFields()) {
            RefField refField = (RefField)getRefManager().getReference(field);
            if (refField != null) {
              getFilter().addIgnoreList(refField);
            }
          }

          for (PsiMethod constructor : primaryKeyClass.getConstructors()) {
            if (constructor.getParameterList().getParameters().length == 0) {
              RefMethod refConstructor = (RefMethod)getRefManager().getReference(constructor);
              if (refConstructor != null) {
                getFilter().addIgnoreList(refConstructor);
              }
            }
          }
        }
      }
    }
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefElement)) return;
        if (getFilter().accepts((RefElement)refEntity)) {
          refEntity.accept(new RefVisitor() {
            public void visitField(final RefField refField) {
              if (refField.getAccessModifier() != PsiModifier.PRIVATE) {
                getContext().enqueueFieldUsagesProcessor(refField, new GlobalInspectionContextImpl.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getFilter().addIgnoreList(refField);
                    return false;
                  }
                });
              }
            }

            public void visitMethod(final RefMethod refMethod) {
              if (refMethod.isAppMain()) {
                getFilter().addIgnoreList(refMethod);
              }
              else if (!refMethod.isExternalOverride() && refMethod.getAccessModifier() != PsiModifier.PRIVATE &&
                       !(refMethod instanceof RefImplicitConstructor)) {
                getContext().enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                  public boolean process(PsiMethod derivedMethod) {
                    getFilter().addIgnoreList(refMethod);
                    return false;
                  }
                });

                getContext().enqueueMethodUsagesProcessor(refMethod, new GlobalInspectionContextImpl.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getFilter().addIgnoreList(refMethod);
                    return false;
                  }
                });
              }
            }

            public void visitClass(final RefClass refClass) {
              if (!refClass.isAnonymous()) {
                getContext().enqueueDerivedClassesProcessor(refClass, new GlobalInspectionContextImpl.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    getFilter().addIgnoreList(refClass);
                    return false;
                  }
                });

                getContext().enqueueClassUsagesProcessor(refClass, new GlobalInspectionContextImpl.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getFilter().addIgnoreList(refClass);
                    return false;
                  }
                });
              }
            }
          });
        }
      }
    });

    return false;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefElement)) return;
        if (getFilter().accepts((RefElement)refEntity)) {
          Element element = XMLExportUtl.createElement(refEntity, parentNode, -1, null);
          @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));

          final HighlightSeverity severity = getCurrentSeverity((RefElement)refEntity);
          final String attributeKey = getTextAttributeKey(severity, null);
          problemClassElement.setAttribute("severity", severity.myName);
          problemClassElement.setAttribute("attribute_key", attributeKey);
          problemClassElement.addContent(InspectionsBundle.message("inspection.visibility.export.results.visibility"));

          element.addContent(problemClassElement);
          Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
          String possibleAccess = getFilter().getPossibleAccess((RefElement)refEntity);
          descriptionElement.addContent(InspectionsBundle.message("inspection.visibility.compose.suggestion", possibleAccess == PsiModifier.PACKAGE_LOCAL ? InspectionsBundle.message("inspection.package.local") : possibleAccess));
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

  private void changeAccessLevel(PsiModifierListOwner psiElement, RefElement refElement, String newAccess) {
    try {
      if (psiElement instanceof PsiVariable) {
        ((PsiVariable)psiElement).normalizeDeclaration();
      }

      PsiModifierList list = psiElement.getModifierList();

      if (psiElement instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)psiElement;
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.getParent() instanceof PsiFile &&
            newAccess == PsiModifier.PRIVATE &&
            list.hasModifierProperty(PsiModifier.FINAL)) {
          list.setModifierProperty(PsiModifier.FINAL, false);
        }
      }

      list.setModifierProperty(newAccess, true);
      RefUtil.getInstance().setAccessModifier(refElement, newAccess);
      getFilter().addIgnoreList(refElement);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void cleanup() {
    super.cleanup();
    if (getFilter() != null) {
      getFilter().cleanup();
    }
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new VisibilityPageComposer(getFilter(), this);
    }
    return myComposer;
  }

  private class AcceptSuggestedAccess extends QuickFixAction {
    private AcceptSuggestedAccess() {
      super(InspectionsBundle.message("inspection.visibility.accept.quickfix"), VisibilityInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (RefElement refElement : refElements) {
        PsiModifierListOwner psiElement = (PsiModifierListOwner)refElement.getElement();
        if (psiElement == null) continue;
        String accessLevel = getFilter().getPossibleAccess(refElement);
        changeAccessLevel(psiElement, refElement, accessLevel);
      }

      return true;
    }
  }
}
