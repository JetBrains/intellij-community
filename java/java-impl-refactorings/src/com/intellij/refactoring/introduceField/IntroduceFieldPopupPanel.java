// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

public class IntroduceFieldPopupPanel extends IntroduceFieldCentralPanel {
  private @Nullable JComboBox myInitializerCombo;
  private DefaultComboBoxModel<BaseExpressionToFieldHandler.InitializationPlace> myInitialisersPlaceModel;

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
                                            BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression);
      if (!myAllowInitInMethod) {
        myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
      }
    } else {
      myInitialisersPlaceModel.removeAllElements();
    }
  }

  void setupSelection(@NotNull Disposable disposable) {
    boolean canBeInitializedInField =
      myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) > -1;
    boolean canBeInitializedInConstructor =
      myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) > -1;
    int canBeInitializedInCurrentMethod =
      myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);

    ReadAction.nonBlocking(
        () -> getSelection(IntroduceFieldDialog.ourLastInitializerPlace, canBeInitializedInField, canBeInitializedInConstructor, canBeInitializedInCurrentMethod))
      .finishOnUiThread(ModalityState.any(),
                        myInitialisersPlaceModel::setSelectedItem)
      .expireWith(disposable)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private BaseExpressionToFieldHandler.InitializationPlace getSelection(BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace,
                                                                        boolean canBeInitializedInField,
                                                                        boolean canBeInitializedInConstructor,
                                                                        int canBeInitializedInCurrentMethod) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod(myParentClass);
    if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD &&
        hasSetUpChoice() && 
        (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) || TestFrameworks.getInstance().isTestClass(myParentClass))) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD;
    }
    else {
      if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR &&
          canBeInitializedInConstructor && myParentClass.getConstructors().length > 0) {
        return BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
      }
      else {
        if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION &&
            canBeInitializedInField) {
          return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
        }
        else {
          if (canBeInitializedInCurrentMethod > -1) {
            return BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD;
          }
          else if (canBeInitializedInField) {
            return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
          }
          else {
            return BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD;
          }
        }
      }
    }
  }

  @Override
  protected void initializeControls(PsiExpression initializerExpression,
                                    BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
  }

  @Override
  public boolean isDeclareFinal() {
    return ourLastCbFinalState && allowFinal();
  }

  @Override
  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    if (myInitializerCombo != null) {
      return (BaseExpressionToFieldHandler.InitializationPlace)myInitializerCombo.getSelectedItem();
    }
    return myInitialisersPlaceModel.getElementAt(0);
  }

  @Override
  public String getFieldVisibility() {
    String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    return visibility;
  }

  @Override
  protected JComponent createInitializerPlacePanel(final ItemListener itemListener, final ItemListener finalUpdater) {

   JPanel groupPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gridBagConstraints =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             JBInsets.emptyInsets(), 0, 0);

    myInitialisersPlaceModel = new DefaultComboBoxModel<>();
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
    if (TestFrameworks.getInstance().isTestClass(myParentClass)) {
      myInitialisersPlaceModel.addElement(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    }
    initializeInitializerPlace(myInitializerExpression, IntroduceFieldDialog.ourLastInitializerPlace);
    if (myInitialisersPlaceModel.getSize() > 1) {
      final JLabel initLabel = new JLabel(RefactoringBundle.message("initialize.in.border.title") + ":");
      initLabel.setDisplayedMnemonic('i');
      gridBagConstraints.insets.left = 5;
      gridBagConstraints.anchor = GridBagConstraints.WEST;
      groupPanel.add(initLabel, gridBagConstraints);
      ComboBox<BaseExpressionToFieldHandler.InitializationPlace> initializersCombo = new ComboBox<>(myInitialisersPlaceModel);
      KeyboardComboSwitcher.setupActions(initializersCombo, myParentClass.getProject());
      initLabel.setLabelFor(initializersCombo);
      initializersCombo.setRenderer(SimpleListCellRenderer.create("", IntroduceFieldPopupPanel::getPresentableText));
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

  @Override
  protected boolean updateInitializationPlaceModel(boolean initializedInSetup, boolean initializedInConstructor, boolean locals) {
    if (locals) {
      myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    }
    if (!initializedInConstructor) {
      myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR);
    }
    if (!initializedInSetup) {
      myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD);
    } else {
      return true;
    }
    return false;
  }

  @Override
  protected boolean hasSetUpChoice() {
    return myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD) > -1;
  }

  @Override
  public void setInitializeInFieldDeclaration() {
    LOG.assertTrue(myInitializerCombo != null);
    myInitializerCombo.setSelectedItem(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
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
  protected void updateInitializerSelection() {
    if (myAllowInitInMethodIfAll || !isReplaceAllOccurrences()) {
      if (myInitialisersPlaceModel.getIndexOf(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD) == -1) {
        myInitialisersPlaceModel.insertElementAt(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, 0);
      }
    } else {
      myInitialisersPlaceModel.removeElement(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
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
