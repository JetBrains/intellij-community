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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.event.ItemListener;

public class InitializerPlaceChooser {
  private static final Logger LOG = Logger.getInstance("#" + InitializerPlaceChooser.class.getName());
  static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  PsiClass myParentClass;

  PsiExpression myInitializerExpression;

  boolean myAllowInitInMethod;
  boolean myAllowInitInMethodIfAll;

  JRadioButton myRbInConstructor;
  JRadioButton myRbInCurrentMethod;
  JRadioButton myRbInFieldDeclaration;
  JRadioButton myRbInSetUp;

  public InitializerPlaceChooser(PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 boolean allowInitInMethod, boolean allowInitInMethodIfAll) {
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myAllowInitInMethod = allowInitInMethod;
    myAllowInitInMethodIfAll = allowInitInMethodIfAll;
  }

  void updateInitializerPlace(boolean replaceAll) {
    if (myAllowInitInMethod) {
      myRbInCurrentMethod.setEnabled(myAllowInitInMethodIfAll || !replaceAll);
      if (!myRbInCurrentMethod.isEnabled() && myRbInCurrentMethod.isSelected()) {
        myRbInCurrentMethod.setSelected(false);
        myRbInFieldDeclaration.setSelected(true);
      }
    }
  }

  void initializeControls(PsiExpression initializerExpression) {
    if (initializerExpression != null) {
      if (!setEnabledInitializationPlaces(initializerExpression, initializerExpression)) {
        myRbInFieldDeclaration.setEnabled(false);
        myRbInConstructor.setEnabled(false);
        if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
      }
      if (!myAllowInitInMethod) {
        myRbInCurrentMethod.setEnabled(false);
      }
    }
    else {
      myRbInConstructor.setEnabled(false);
      myRbInCurrentMethod.setEnabled(false);
      myRbInFieldDeclaration.setEnabled(false);
      if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
    }

    final PsiMethod setUpMethod = TestUtil.findSetUpMethod(myParentClass);
    if (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) && myRbInSetUp.isEnabled() ||
        ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD &&
        TestUtil.isTestClass(myParentClass) &&
        myRbInSetUp.isEnabled()) {
      myRbInSetUp.setSelected(true);
    }
    else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
      if (myRbInConstructor.isEnabled()) {
        myRbInConstructor.setSelected(true);
      }
      else {
        selectInCurrentMethod();
      }
    }
    else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
      if (myRbInFieldDeclaration.isEnabled()) {
        myRbInFieldDeclaration.setSelected(true);
      }
      else {
        selectInCurrentMethod();
      }
    }
    else {
      selectInCurrentMethod();
    }
  }

  void selectInCurrentMethod() {
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

  JComponent createInitializerPlacePanel() {

    final JPanel initializationPanel = new JPanel();
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

    return initializationPanel;
  }

  static boolean setEnabledInitializationPlaces(PsiElement initializerPart, PsiElement initializer) {
    if (initializerPart instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)initializerPart;
      if (refExpr.getQualifierExpression() == null) {
        PsiElement refElement = refExpr.resolve();
        if (refElement == null ||
            (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) &&
            !PsiTreeUtil.isAncestor(initializer, refElement, true)) {
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

  boolean allowFinal(boolean willBeDeclaredStatic, boolean isCurrentMethodConstructor) {
    boolean allowFinal = myRbInFieldDeclaration.isSelected() || (myRbInConstructor.isSelected() && !willBeDeclaredStatic);
    if (myRbInCurrentMethod.isSelected() && isCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return allowFinal;
  }

  void addItemListener(ItemListener listener) {
    myRbInConstructor.addItemListener(listener);
    myRbInCurrentMethod.addItemListener(listener);
    myRbInFieldDeclaration.addItemListener(listener);
    if (myRbInSetUp != null) myRbInSetUp.addItemListener(listener);
  }
}