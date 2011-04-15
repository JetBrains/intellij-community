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

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

/**
 * User: anna
 * Date: 4/8/11
 */
public class IntroduceFieldPopupPanel extends IntroduceFieldCentralPanel {
  private JComboBox myInitializerCombo;
  private JComboBox myVisibilityCombo;
  private DefaultComboBoxModel myInitialisersPlaceModel;

  public IntroduceFieldPopupPanel(PsiClass parentClass,
                                  PsiExpression initializerExpression,
                                  PsiLocalVariable localVariable,
                                  boolean isCurrentMethodConstructor,
                                  boolean isInvokedOnDeclaration,
                                  boolean willBeDeclaredStatic,
                                  int occurrencesCount,
                                  boolean allowInitInMethod,
                                  boolean allowInitInMethodIfAll,
                                  TypeSelectorManager typeSelectorManager) {
    super(parentClass, initializerExpression, localVariable, isCurrentMethodConstructor, isInvokedOnDeclaration, willBeDeclaredStatic,
          occurrencesCount, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);
  }

  protected void initializeControls(PsiExpression initializerExpression, BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    super.initializeControls(initializerExpression, ourLastInitializerPlace);
    initializeInitializerPlace(initializerExpression, ourLastInitializerPlace);
    String ourLastVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    setVisibility(ourLastVisibility);
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
    if (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) && setupEnabled ||
        ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD && TestUtil.isTestClass(myParentClass) &&
        setupEnabled) {
      myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    }
    else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
      if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) > -1) {
        myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
      } else {
        selectInCurrentMethod();
      }
    } else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
      if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) > -1) {
        myInitialisersPlaceModel.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
      } else {
        selectInCurrentMethod();
      }
    } else {
      selectInCurrentMethod();
    }
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
    return (BaseExpressionToFieldHandler.InitializationPlace)myInitializerCombo.getSelectedItem();
  }

  public String getFieldVisibility() {
    return (String)myVisibilityCombo.getSelectedItem();
  }

  protected JComponent createInitializerPlacePanel(final ItemListener itemListener, final ItemListener finalUpdater) {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

   JPanel groupPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gridBagConstraints =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(0, 5, 0, 0), 0, 0);
    gridBagConstraints.insets.top = 5;
    final JLabel initLabel = new JLabel(RefactoringBundle.message("initialize.in.border.title") + ":");
    initLabel.setDisplayedMnemonic('i');
    groupPanel.add(initLabel, gridBagConstraints);

    myInitialisersPlaceModel = new DefaultComboBoxModel();
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
    if (TestUtil.isTestClass(myParentClass)) {
      myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    }
    myInitializerCombo = new JComboBox(myInitialisersPlaceModel);
    InplaceIntroduceConstantPopup.appendActions(myInitializerCombo, myParentClass.getProject());
    initLabel.setLabelFor(myInitializerCombo);
    myInitializerCombo.setRenderer(new ListCellRendererWrapper<BaseExpressionToFieldHandler.InitializationPlace>(myInitializerCombo) {
      @Override
      public void customize(JList list,
                            BaseExpressionToFieldHandler.InitializationPlace value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD) {
          setText("current method");
        } else if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
          setText("constructor");
        } else if (value == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
          setText("field declaration");
        } else {
          setText("setUp");
        }
      }
    });
    myInitializerCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        itemListener.itemStateChanged(null);
        finalUpdater.itemStateChanged(null);
      }
    });
    gridBagConstraints.gridx = 1;
    gridBagConstraints.insets.top = 0;
    gridBagConstraints.insets.left = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    groupPanel.add(myInitializerCombo, gridBagConstraints);



    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets.top = 8;
    gridBagConstraints.insets.left = 5;
    myVisibilityCombo = InplaceIntroduceConstantPopup.createVisibilityCombo(groupPanel, gridBagConstraints, myParentClass.getProject());

    mainPanel.add(groupPanel, BorderLayout.CENTER);

    return mainPanel;
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
          enableFinal(false);
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

  public void addVisibilityListener(final ChangeListener changeListener) {
    myVisibilityCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        changeListener.stateChanged(null);
      }
    });
  }

  public void setInitializeInFieldDeclaration() {
    myInitializerCombo.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
  }

  public void setVisibility(String visibility) {
    myVisibilityCombo.setSelectedItem(visibility);
  }

  @Override
  protected boolean allowFinal() {
    final Object selectedItem = myInitializerCombo.getSelectedItem();
    boolean allowFinal = selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION ||
                         (selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR && !myWillBeDeclaredStatic);
    if (selectedItem == BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return allowFinal;
  }

  @Override
  protected boolean shouldUpdateTypeSelector() {
    return false;
  }

  protected JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 0), 0, 0);
    constraints.insets.bottom = 5;
    panel.add(initializerPlacePanel, constraints);
    constraints.insets.left = 5;
    panel.add(checkboxPanel, constraints);
    return panel;
  }
}
