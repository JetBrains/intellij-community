// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;

public class IntroduceFieldDialogPanel extends IntroduceFieldCentralPanel {
  private JRadioButton myRbInConstructor;
  private JRadioButton myRbInCurrentMethod;
  private JRadioButton myRbInFieldDeclaration;
  private JRadioButton myRbInSetUp;
  private JavaVisibilityPanel myVisibilityPanel;

  public IntroduceFieldDialogPanel(PsiClass parentClass,
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

  @Override
  protected void initializeControls(PsiExpression initializerExpression, BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    initializeInitializerPlace(initializerExpression, ourLastInitializerPlace);
    String ourLastVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (ourLastVisibility == null) {
      ourLastVisibility = PsiModifier.PRIVATE;
    }
    myVisibilityPanel.setVisibility(ourLastVisibility);
    super.initializeControls(initializerExpression, ourLastInitializerPlace);
  }

  @Override
  protected void initializeInitializerPlace(PsiExpression initializerExpression,
                                            BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression);
      if (!myAllowInitInMethod) {
        myRbInCurrentMethod.setEnabled(false);
      }
    } else {
      myRbInConstructor.setEnabled(false);
      myRbInCurrentMethod.setEnabled(false);
      myRbInFieldDeclaration.setEnabled(false);
      if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
    }

    final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod(myParentClass);
    if (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) && myRbInSetUp.isEnabled() ||
        ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD && TestFrameworks.getInstance().isTestClass(myParentClass) && myRbInSetUp.isEnabled()) {
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

  @Override
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

  @Override
  public String getFieldVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  @Override
  protected JComponent createInitializerPlacePanel(ItemListener itemListener, ItemListener finalUpdater) {
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
    myRbInFieldDeclaration.setText(JavaRefactoringBundle.message("field.declaration.radio"));

    myRbInConstructor = new JRadioButton();
    myRbInConstructor.setFocusable(false);
    myRbInConstructor.setText(RefactoringBundle.message("class.constructors.radio"));



    initializationPanel.add(myRbInCurrentMethod);
    initializationPanel.add(myRbInFieldDeclaration);
    initializationPanel.add(myRbInConstructor);

    if (TestFrameworks.getInstance().isTestClass(myParentClass)) {
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

    myRbInConstructor.addItemListener(itemListener);
    myRbInCurrentMethod.addItemListener(itemListener);
    myRbInFieldDeclaration.addItemListener(itemListener);
    myRbInConstructor.addItemListener(finalUpdater);
    myRbInCurrentMethod.addItemListener(finalUpdater);
    myRbInFieldDeclaration.addItemListener(finalUpdater);
    if (myRbInSetUp != null) myRbInSetUp.addItemListener(finalUpdater);

//    modifiersPanel.add(myCbFinal);
//    modifiersPanel.add(myCbStatic);

    JPanel groupPanel = new JPanel(new GridLayout(1, 2));
    groupPanel.add(initializationPanel);

    myVisibilityPanel = new JavaVisibilityPanel(false, false);
    groupPanel.add(myVisibilityPanel);

    mainPanel.add(groupPanel, BorderLayout.CENTER);

    return mainPanel;
  }

  @Override
  protected boolean updateInitializationPlaceModel(boolean initializedInSetup, boolean initializedInConstructor, boolean locals) {
    if (locals) {
      myRbInFieldDeclaration.setEnabled(false);
    }
    myRbInConstructor.setEnabled(initializedInConstructor);
    enableFinal(false);
    if (myRbInSetUp != null){
      if (!initializedInSetup) {
        myRbInSetUp.setEnabled(false);
      } else {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean hasSetUpChoice() {
    return myRbInSetUp != null;
  }

  @Override
  public void setInitializeInFieldDeclaration() {
    myRbInFieldDeclaration.setSelected(true);
  }

  @Override
  protected boolean allowFinal() {
    boolean allowFinal = myRbInFieldDeclaration.isSelected() || (myRbInConstructor.isSelected() && !myWillBeDeclaredStatic);
    if (myRbInCurrentMethod.isSelected() && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return super.allowFinal() && allowFinal;
  }

  @Override
  protected void updateInitializerSelection() {
    myRbInCurrentMethod.setEnabled(myAllowInitInMethodIfAll || !isReplaceAllOccurrences());
    if (!myRbInCurrentMethod.isEnabled() && myRbInCurrentMethod.isSelected()) {
      myRbInCurrentMethod.setSelected(false);
      myRbInFieldDeclaration.setSelected(true);
    }
  }

  @Override
  protected JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel) {
    JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             JBInsets.emptyInsets(), 0, 0);
    panel.add(initializerPlacePanel, constraints);
    panel.add(checkboxPanel, constraints);
    return panel;
  }
}
