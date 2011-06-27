/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestUtil;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * User: anna
 * Date: 4/8/11
 */
public class IntroduceFieldPopupPanel extends IntroduceFieldCentralPanel {
  private @Nullable JComboBox myInitializerCombo;
  private JComboBox myVisibilityCombo;
  private DefaultComboBoxModel myInitialisersPlaceModel;

  public IntroduceFieldPopupPanel(PsiClass parentClass,
                                  PsiExpression initializerExpression,
                                  PsiLocalVariable localVariable,
                                  boolean isCurrentMethodConstructor,
                                  boolean isInvokedOnDeclaration,
                                  boolean willBeDeclaredStatic,
                                  PsiExpression[] occurrences,
                                  boolean allowInitInMethod,
                                  boolean allowInitInMethodIfAll,
                                  TypeSelectorManager typeSelectorManager) {
    super(parentClass, initializerExpression, localVariable, isCurrentMethodConstructor, isInvokedOnDeclaration, willBeDeclaredStatic,
          occurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);
  }

  protected void initializeInitializerPlace(PsiExpression initializerExpression,
                                            BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression, initializerExpression);
      if (!myAllowInitInMethod) {
        myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
      }
    } else {
      myInitialisersPlaceModel.removeAllElements();
    }

    final PsiMethod setUpMethod = TestUtil.findSetUpMethod(myParentClass);
    final boolean setupEnabled = myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD) > -1;
    if (setupEnabled && (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) ||
                         TestUtil.isTestClass(myParentClass))) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    }
    else if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) > -1 && myParentClass.getConstructors().length > 0) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
    }
    else if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) > -1) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    }
    else {
      selectInCurrentMethod();
    }
  }

  @Override
  protected void initializeControls(PsiExpression initializerExpression,
                                    BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
  }

  @Override
  public boolean isDeclareFinal() {
    return allowFinal();
  }

  private void selectInCurrentMethod() {
    if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD) > -1) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    }
    else if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) > -1) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    }
    else {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    }
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    if (myInitializerCombo != null) {
      return (BaseExpressionToFieldHandler.InitializationPlace)myInitializerCombo.getSelectedItem();
    }
    return (BaseExpressionToFieldHandler.InitializationPlace)myInitialisersPlaceModel.getElementAt(0);
  }

  public String getFieldVisibility() {
    String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    return visibility;
  }

  protected JComponent createInitializerPlacePanel(final ItemListener itemListener, final ItemListener finalUpdater) {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

   JPanel groupPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gridBagConstraints =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(0, 0, 0, 0), 0, 0);

    myInitialisersPlaceModel = new DefaultComboBoxModel();
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
    if (TestUtil.isTestClass(myParentClass)) {
      myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    }
    initializeInitializerPlace(myInitializerExpression, InplaceIntroduceFieldPopup.ourLastInitializerPlace);
    if (myInitialisersPlaceModel.getSize() > 1) {
      final JLabel initLabel = new JLabel(RefactoringBundle.message("initialize.in.border.title") + ":");
      initLabel.setDisplayedMnemonic('i');
      gridBagConstraints.insets.left = 5;
      gridBagConstraints.anchor = GridBagConstraints.WEST;
      groupPanel.add(initLabel, gridBagConstraints);
      JComboBox initializersCombo = new JComboBox(myInitialisersPlaceModel);
      InplaceCombosUtil.appendActions(initializersCombo, myParentClass.getProject());
      initLabel.setLabelFor(initializersCombo);
      initializersCombo.setRenderer(new ListCellRendererWrapper<BaseExpressionToFieldHandler.InitializationPlace>(initializersCombo) {
        @Override
        public void customize(JList list,
                              BaseExpressionToFieldHandler.InitializationPlace value,
                              int index,
                              boolean selected,
                              boolean hasFocus) {
          setText(getPresentableText(value));
        }
      });
      initializersCombo.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          itemListener.itemStateChanged(null);
          finalUpdater.itemStateChanged(null);
        }
      });
      gridBagConstraints.gridx = 1;
      gridBagConstraints.insets.top = 0;
      gridBagConstraints.insets.left = 0;
      groupPanel.add(initializersCombo, gridBagConstraints);
      myInitializerCombo = initializersCombo;
    } /*else if (myInitialisersPlaceModel.getSize() == 1){
      gridBagConstraints.gridwidth = 2;
      groupPanel.add(new JLabel("Initialize field in " +
                                getPresentableText((BaseExpressionToFieldHandler.InitializationPlace)myInitialisersPlaceModel.getElementAt(0))),
                     gridBagConstraints);
      gridBagConstraints.gridwidth = 1;
    }*/


    return groupPanel;
  }

  @Nullable
  private static String getPresentableText(BaseExpressionToFieldHandler.InitializationPlace value) {
    if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD) {
      return "current method";
    } else if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
      return "constructor";
    } else if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
      return "field declaration";
    } else if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD){
      return "setUp";
    }
    return null;
  }

  protected boolean setEnabledInitializationPlaces(PsiElement initializerPart, PsiElement initializer) {
    if (initializerPart instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) initializerPart;
      if (refExpr.getQualifierExpression() == null) {
        PsiElement refElement = refExpr.resolve();
        if (refElement == null ||
            (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) &&
            !PsiTreeUtil.isAncestor(initializer, refElement, true)) {
          myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
          myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
          myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
          return false;
        }
      }
    }
    PsiElement[] children = initializerPart.getChildren();
    for (PsiElement child : children) {
      if (!setEnabledInitializationPlaces(child, initializer)) return false;
    }
    return true;
  }

  public void setInitializeInFieldDeclaration() {
    LOG.assertTrue(myInitializerCombo != null);
    myInitializerCombo.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
  }

  public void setVisibility(String visibility) {
    myVisibilityCombo.setSelectedItem(visibility);
  }

  @Override
  protected void updateCbFinal() {
  }

  @Override
  protected boolean allowFinal() {
    final Object selectedItem = getInitializerPlace();
    boolean allowFinal = selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION ||
                         (selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR && !myWillBeDeclaredStatic);
    if (selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return super.allowFinal() && allowFinal;
  }

  @Override
  protected boolean shouldUpdateTypeSelector() {
    return false;
  }

  @Override
  protected JPanel appendCheckboxes(ItemListener itemListener) {
    final JPanel panel = new JPanel(new GridBagLayout());
    appendOccurrences(itemListener, new GridBagConstraints(0,0,1,1,0,0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0), panel);
    return panel;
  }

  protected JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 0), 0, 0);
    panel.add(initializerPlacePanel, constraints);
    constraints.gridy++;
    panel.add(checkboxPanel, constraints);
    return panel;
  }
}
