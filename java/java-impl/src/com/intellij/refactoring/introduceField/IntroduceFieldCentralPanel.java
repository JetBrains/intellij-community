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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/16/11
 */
public class IntroduceFieldCentralPanel {
   private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceFieldDialog");

  private static boolean ourLastCbFinalState = false;
  private static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myIsCurrentMethodConstructor;
  private final boolean myIsInvokedOnDeclaration;
  private final boolean myWillBeDeclaredStatic;
  private final int myOccurrencesCount;
  private final boolean myAllowInitInMethod;
  private final boolean myAllowInitInMethodIfAll;
  private final TypeSelectorManager myTypeSelectorManager;


  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbDeleteVariable;
  private StateRestoringCheckBox myCbFinal;

  private JRadioButton myRbInConstructor;
  private JRadioButton myRbInCurrentMethod;
  private JRadioButton myRbInFieldDeclaration;
  private JRadioButton myRbInSetUp;

  private JavaVisibilityPanel myVisibilityPanel;

  public IntroduceFieldCentralPanel(PsiClass parentClass,
                                    PsiExpression initializerExpression,
                                    PsiLocalVariable localVariable,
                                    boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                                    int occurrencesCount, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                                    TypeSelectorManager typeSelectorManager) {
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myIsCurrentMethodConstructor = isCurrentMethodConstructor;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;
    myOccurrencesCount = occurrencesCount;
    myAllowInitInMethod = allowInitInMethod;
    myAllowInitInMethodIfAll = allowInitInMethodIfAll;
    myTypeSelectorManager = typeSelectorManager;
  }

  void initializeControls(PsiExpression initializerExpression) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression, initializerExpression);
      if (!myAllowInitInMethod) {
        myRbInCurrentMethod.setEnabled(false);
      }
    } else {
      myRbInConstructor.setEnabled(false);
      myRbInCurrentMethod.setEnabled(false);
      myRbInFieldDeclaration.setEnabled(false);
      if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
    }

    final PsiMethod setUpMethod = TestUtil.findSetUpMethod(myParentClass);
    if (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) && myRbInSetUp.isEnabled() ||
        ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD && TestUtil.isTestClass(myParentClass) && myRbInSetUp.isEnabled()) {
      myRbInSetUp.setSelected(true);
    }
    else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
      if (myRbInConstructor.isEnabled()) {
        myRbInConstructor.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
      if (myRbInFieldDeclaration.isEnabled()) {
        myRbInFieldDeclaration.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else {
      selectInCurrentMethod();
    }
    String ourLastVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    myVisibilityPanel.setVisibility(ourLastVisibility);
    myCbFinal.setSelected(myCbFinal.isEnabled() && ourLastCbFinalState);
  }

  private void selectInCurrentMethod() {
    if (myRbInCurrentMethod.isEnabled()) {
      myRbInCurrentMethod.setSelected(true);
    }
    else if (myRbInFieldDeclaration.isEnabled()) {
      myRbInFieldDeclaration.setSelected(true);
    }
    else {
      myRbInCurrentMethod.setSelected(true);
    }
  }


  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    if (myRbInConstructor.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
    }
    if (myRbInCurrentMethod.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD;
    }
    if (myRbInFieldDeclaration.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
    }
    if (myRbInSetUp != null && myRbInSetUp.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD;
    }

    LOG.assertTrue(false);
    return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
  }


  public String getFieldVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  public boolean isReplaceAllOccurrences() {
    if (myIsInvokedOnDeclaration) return true;
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public boolean isDeleteVariable() {
    if (myIsInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  public boolean isDeclareFinal() {
    return myCbFinal.isSelected();
  }



  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    final Insets standardInsets = new Insets(0, 0, 0, 0);
    gbConstraints.insets = standardInsets;

    panel.add(createInitializerPlacePanel(), gbConstraints);
    ItemListener itemListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (myCbReplaceAll != null && myAllowInitInMethod) {
          myRbInCurrentMethod.setEnabled(myAllowInitInMethodIfAll || !myCbReplaceAll.isSelected());
          if (!myRbInCurrentMethod.isEnabled() && myRbInCurrentMethod.isSelected()) {
            myRbInCurrentMethod.setSelected(false);
            myRbInFieldDeclaration.setSelected(true);
          }
        }
        updateTypeSelector();
      }
    };
    ItemListener finalUpdater = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateCbFinal();
      }
    };
    myRbInConstructor.addItemListener(itemListener);
    myRbInCurrentMethod.addItemListener(itemListener);
    myRbInFieldDeclaration.addItemListener(itemListener);
    myRbInConstructor.addItemListener(finalUpdater);
    myRbInCurrentMethod.addItemListener(finalUpdater);
    myRbInFieldDeclaration.addItemListener(finalUpdater);
    if (myRbInSetUp != null) myRbInSetUp.addItemListener(finalUpdater);
    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurrences.of.expression.0.occurrences", myOccurrencesCount));
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
      if (myIsInvokedOnDeclaration) {
        myCbReplaceAll.setEnabled(false);
        myCbReplaceAll.setSelected(true);
      }
    }

    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = new Insets(0, 8, 0, 0);
      }
      myCbDeleteVariable = new StateRestoringCheckBox();
      myCbDeleteVariable.setText(RefactoringBundle.message("delete.variable.declaration"));
      panel.add(myCbDeleteVariable, gbConstraints);
      if (myIsInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      } else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
                new ItemListener() {
                  public void itemStateChanged(ItemEvent e) {
                    updateCbDeleteVariable();
                  }
                }
        );
      }
      gbConstraints.insets = standardInsets;
    }
    myCbFinal.addItemListener(itemListener);
//    myCbStatic.addItemListener(itemListener);
//    myCbStatic.addItemListener(finalUpdater);
//    myCbStatic.addItemListener(
//      new ItemListener() {
//        public void itemStateChanged(ItemEvent e) {
//          updateNameList();
//        }
//      }
//    );

    updateTypeSelector();
    return panel;
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private JComponent createInitializerPlacePanel() {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

    JPanel initializationPanel = new JPanel();
    initializationPanel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("initialize.in.border.title")));
    initializationPanel.setLayout(new BoxLayout(initializationPanel, BoxLayout.Y_AXIS));


    myRbInCurrentMethod = new JRadioButton();
    myRbInCurrentMethod.setFocusable(false);
    myRbInCurrentMethod.setText(RefactoringBundle.message("current.method.radio"));
    myRbInCurrentMethod.setEnabled(myAllowInitInMethod);

    myRbInFieldDeclaration = new JRadioButton();
    myRbInFieldDeclaration.setFocusable(false);
    myRbInFieldDeclaration.setText(RefactoringBundle.message("field.declaration.radio"));

    myRbInConstructor = new JRadioButton();
    myRbInConstructor.setFocusable(false);
    myRbInConstructor.setText(RefactoringBundle.message("class.constructors.radio"));

    myVisibilityPanel = new JavaVisibilityPanel(false, false);

    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setFocusable(false);
    myCbFinal.setText(RefactoringBundle.message("declare.final"));

    initializationPanel.add(myRbInCurrentMethod);
    initializationPanel.add(myRbInFieldDeclaration);
    initializationPanel.add(myRbInConstructor);

    if (TestUtil.isTestClass(myParentClass)) {
      myRbInSetUp = new JRadioButton();
      myRbInSetUp.setFocusable(false);
      myRbInSetUp.setText(RefactoringBundle.message("setup.method.radio"));
      initializationPanel.add(myRbInSetUp);
    }

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInCurrentMethod);
    bg.add(myRbInFieldDeclaration);
    bg.add(myRbInConstructor);
    if (myRbInSetUp != null) bg.add(myRbInSetUp);



//    modifiersPanel.add(myCbFinal);
//    modifiersPanel.add(myCbStatic);

    JPanel groupPanel = new JPanel(new GridLayout(1, 2));
    groupPanel.add(initializationPanel);
    groupPanel.add(myVisibilityPanel);
    mainPanel.add(groupPanel, BorderLayout.CENTER);
    mainPanel.add(myCbFinal, BorderLayout.SOUTH);

    return mainPanel;
  }

  private void updateCbFinal() {
    boolean allowFinal = myRbInFieldDeclaration.isSelected() || (myRbInConstructor.isSelected() && !myWillBeDeclaredStatic);
    if (myRbInCurrentMethod.isSelected() && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    if (!allowFinal) {
      myCbFinal.makeUnselectable(false);
    } else {
      myCbFinal.makeSelectable();
    }
  }




  private boolean setEnabledInitializationPlaces(PsiElement initializerPart, PsiElement initializer) {
    if (initializerPart instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) initializerPart;
      if (refExpr.getQualifierExpression() == null) {
        PsiElement refElement = refExpr.resolve();
        if (refElement == null ||
            (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) &&
            !PsiTreeUtil.isAncestor(initializer, refElement, true)) {
          myRbInFieldDeclaration.setEnabled(false);
          myRbInConstructor.setEnabled(false);
          if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
          myCbFinal.setEnabled(false);
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

  public void addOccurrenceListener(ItemListener itemListener) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.addItemListener(itemListener);
    }
  }

  public void addVisibilityListener(ChangeListener changeListener) {
    myVisibilityPanel.addListener(changeListener);
  }

  public void addFinalListener(ItemListener itemListener) {
    myCbFinal.addItemListener(itemListener);
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.setSelected(replaceAllOccurrences);
    }
  }

  public void setCreateFinal(boolean createFinal) {
    myCbFinal.setSelected(createFinal);
  }

  public void setInitializeInFieldDeclaration() {
    myRbInFieldDeclaration.setSelected(true);
  }

  public void setVisibility(String visibility) {
    myVisibilityPanel.setVisibility(visibility);
  }
}
