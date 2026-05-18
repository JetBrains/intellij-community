// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

public class IntroduceFieldPopupPanel extends IntroduceFieldCentralPanel {
  private @Nullable JComboBox<JavaIntroduceFieldService.InitializationPlace> myInitializerCombo;
  private DefaultComboBoxModel<JavaIntroduceFieldService.InitializationPlace> myInitialisersPlaceModel;

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

  @Override
  protected void initializeInitializerPlace(PsiExpression initializerExpression,
                                            JavaIntroduceFieldService.InitializationPlace ourLastInitializerPlace) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression, hasSetUpChoice());
      if (!myAllowInitInMethod) {
        myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD);
      }
      boolean inOnlyConstructor = myIsCurrentMethodConstructor && myParentClass.getConstructors().length == 1;
      if (myWillBeDeclaredStatic || inOnlyConstructor) {
        myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR);
      }
    } else {
      myInitialisersPlaceModel.removeAllElements();
    }
  }

  void setupSelection(@NotNull Disposable disposable) {
    boolean canBeInitializedInField =
      myInitialisersPlaceModel.getIndexOf(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION) > -1;
    boolean canBeInitializedInConstructor =
      myInitialisersPlaceModel.getIndexOf(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR) > -1;
    int canBeInitializedInCurrentMethod =
      myInitialisersPlaceModel.getIndexOf(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD);

    ReadAction.nonBlocking(
        () -> getSelection(IntroduceFieldDialog.ourLastInitializerPlace, canBeInitializedInField, canBeInitializedInConstructor, canBeInitializedInCurrentMethod))
      .finishOnUiThread(ModalityState.any(),
                        myInitialisersPlaceModel::setSelectedItem)
      .expireWith(disposable)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private JavaIntroduceFieldService.InitializationPlace getSelection(JavaIntroduceFieldService.InitializationPlace ourLastInitializerPlace,
                                                                     boolean canBeInitializedInField,
                                                                     boolean canBeInitializedInConstructor,
                                                                     int canBeInitializedInCurrentMethod) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod(myParentClass);
    if (ourLastInitializerPlace == JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD &&
        hasSetUpChoice() &&
        (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) || TestFrameworks.getInstance().isTestClass(myParentClass))) {
      return JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD;
    }
    else {
      if (ourLastInitializerPlace == JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR &&
          canBeInitializedInConstructor && myParentClass.getConstructors().length > 0) {
        return JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR;
      }
      else {
        if (ourLastInitializerPlace == JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION &&
            canBeInitializedInField) {
          return JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION;
        }
        else {
          if (canBeInitializedInCurrentMethod > -1) {
            return JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD;
          }
          else if (canBeInitializedInField) {
            return JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION;
          }
          else {
            return JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD;
          }
        }
      }
    }
  }

  @Override
  protected void initializeControls(PsiExpression initializerExpression,
                                    JavaIntroduceFieldService.InitializationPlace ourLastInitializerPlace) {
  }

  @Override
  public boolean isDeclareFinal() {
    return ourLastCbFinalState && allowFinal();
  }

  @Override
  public JavaIntroduceFieldService.InitializationPlace getInitializerPlace() {
    if (myInitializerCombo != null) {
      return (JavaIntroduceFieldService.InitializationPlace)myInitializerCombo.getSelectedItem();
    }
    return myInitialisersPlaceModel.getElementAt(0);
  }

  @Override
  public String getFieldVisibility() {
    return new IntroduceFieldHelper().getVisibility();
  }

  @Override
  protected JComponent createInitializerPlacePanel(final ItemListener itemListener, final ItemListener finalUpdater) {

   JPanel groupPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gridBagConstraints =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             JBInsets.emptyInsets(), 0, 0);

    myInitialisersPlaceModel = new DefaultComboBoxModel<>();
    myInitialisersPlaceModel.addElement(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD);
    myInitialisersPlaceModel.addElement(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION);
    myInitialisersPlaceModel.addElement(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR);
    if (!DumbService.isDumb(myParentClass.getProject()) && TestFrameworks.getInstance().isTestClass(myParentClass)) {
      myInitialisersPlaceModel.addElement(JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD);
    }
    initializeInitializerPlace(myInitializerExpression, IntroduceFieldDialog.ourLastInitializerPlace);
    if (myInitialisersPlaceModel.getSize() > 1) {
      final JLabel initLabel = new JLabel(RefactoringBundle.message("initialize.in.border.title") + ":");
      gridBagConstraints.insets.left = 5;
      gridBagConstraints.anchor = GridBagConstraints.WEST;
      groupPanel.add(initLabel, gridBagConstraints);
      ComboBox<JavaIntroduceFieldService.InitializationPlace> initializersCombo = new ComboBox<>(myInitialisersPlaceModel);
      KeyboardComboSwitcher.setupActions(initializersCombo, myParentClass.getProject());
      initLabel.setLabelFor(initializersCombo);
      initializersCombo.setRenderer(BuilderKt.textListCellRenderer("", place -> JavaIntroduceFieldService.InitializationPlace.getShortPresentableText(place)));
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
    }
    return groupPanel;
  }

  public static @Nullable String getPresentableText(@Nullable JavaIntroduceFieldService.InitializationPlace value) {
    if (value == JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD) {
      return "current method";
    } else if (value == JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR) {
      return "constructor";
    } else if (value == JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION) {
      return "field declaration";
    } else if (value == JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD){
      return "setUp";
    }
    return null;
  }

  @Override
  protected boolean updateInitializationPlaceModel(boolean initializedInSetup, boolean initializedInConstructor, boolean locals) {
    if (locals) {
      myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION);
    }
    if (!initializedInConstructor) {
      myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR);
    }
    if (!initializedInSetup) {
      myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD);
    } else {
      return true;
    }
    return false;
  }

  @Override
  protected boolean hasSetUpChoice() {
    return myInitialisersPlaceModel.getIndexOf(JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD) > -1;
  }

  @Override
  public void setInitializeInFieldDeclaration() {
    LOG.assertTrue(myInitializerCombo != null);
    myInitializerCombo.setSelectedItem(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION);
  }

  @Override
  protected void updateCbFinal() {
  }

  @Override
  protected boolean allowFinal() {
    final Object selectedItem = getInitializerPlace();
    boolean allowFinal = selectedItem == JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION ||
                         (selectedItem == JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR && !myWillBeDeclaredStatic);
    if (selectedItem == JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return super.allowFinal() && allowFinal;
  }

  @Override
  protected void updateInitializerSelection() {
    if (myAllowInitInMethodIfAll || !isReplaceAllOccurrences()) {
      if (myInitialisersPlaceModel.getIndexOf(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD) == -1) {
        myInitialisersPlaceModel.insertElementAt(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD, 0);
      }
    } else {
      myInitialisersPlaceModel.removeElement(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD);
    }
  }

  @Override
  protected boolean shouldUpdateTypeSelector() {
    return false;
  }

  @Override
  protected JPanel appendCheckboxes(ItemListener itemListener) {
    final JPanel panel = new JPanel(new GridBagLayout());
    appendOccurrences(itemListener, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                           JBInsets.emptyInsets(), 0, 0), panel);
    return panel;
  }

  @Override
  protected JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             JBInsets.emptyInsets(), 0, 0);
    panel.add(initializerPlacePanel, constraints);
    constraints.gridy++;
    panel.add(checkboxPanel, constraints);
    return panel;
  }
}
