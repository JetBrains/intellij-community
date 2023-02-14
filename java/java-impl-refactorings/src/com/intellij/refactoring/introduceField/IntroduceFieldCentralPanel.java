// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashSet;
import java.util.Set;

public abstract class IntroduceFieldCentralPanel {
  protected static final Logger LOG = Logger.getInstance(IntroduceFieldDialog.class);

  private static final String INTRODUCE_FIELD_FINAL_CHECKBOX = "introduce.final.checkbox";
  public static boolean ourLastCbFinalState = PropertiesComponent.getInstance().getBoolean(INTRODUCE_FIELD_FINAL_CHECKBOX, true);

  protected final PsiClass myParentClass;
  protected final PsiExpression myInitializerExpression;
  protected final PsiLocalVariable myLocalVariable;
  protected final boolean myIsCurrentMethodConstructor;
  protected final boolean myIsInvokedOnDeclaration;
  protected final boolean myWillBeDeclaredStatic;
  protected final int myOccurrencesCount;
  protected final boolean myAllowInitInMethod;
  protected final boolean myAllowInitInMethodIfAll;
  protected final TypeSelectorManager myTypeSelectorManager;


  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbDeleteVariable;
  private StateRestoringCheckBox myCbFinal;
  private boolean myHasWriteAccess;

  public IntroduceFieldCentralPanel(PsiClass parentClass,
                                    PsiExpression initializerExpression,
                                    PsiLocalVariable localVariable,
                                    boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                                    PsiExpression[] occurrences, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                                    TypeSelectorManager typeSelectorManager) {
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myIsCurrentMethodConstructor = isCurrentMethodConstructor;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;
    myOccurrencesCount = occurrences.length;
    myHasWriteAccess = false;
    for (PsiExpression occurrence : occurrences) {
      if (PsiUtil.isAccessedForWriting(occurrence)) {
        myHasWriteAccess = true;
        break;
      }
    }
    myAllowInitInMethod = allowInitInMethod;
    myAllowInitInMethodIfAll = allowInitInMethodIfAll;
    myTypeSelectorManager = typeSelectorManager;
  }

  protected boolean setEnabledInitializationPlaces(@NotNull final PsiExpression initializer) {
    final Set<PsiField> fields = new HashSet<>();
    final Ref<Boolean> refsLocal = new Ref<>(false);
    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() == null) {
          final PsiElement resolve = expression.resolve();
          if (resolve == null ||
              resolve instanceof PsiVariable && !PsiTreeUtil.isAncestor(initializer, resolve, true)) {
            if (resolve instanceof PsiField) {
              if (!((PsiField)resolve).hasInitializer()) {
                fields.add((PsiField)resolve);
              }
            }
            else {
              refsLocal.set(true);
              stopWalking();
            }
          }
        }
      }
    });

    final boolean locals = refsLocal.get();
    boolean superOrThis = IntroduceFieldHandler.isInSuperOrThis(initializer);
    if (!locals && fields.isEmpty() && !superOrThis) {
      return true;
    }
    return updateInitializationPlaceModel(!locals && initializedInSetUp(fields),
                                          !locals && !superOrThis && initializedInConstructor(fields), 
                                          locals || !fields.isEmpty());
  }

  private static boolean initializedInConstructor(Set<PsiField> fields) {
    for (PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
    }
    return true;
  }

  private boolean initializedInSetUp(Set<PsiField> fields) {
    if (hasSetUpChoice()) {
      nextField:
      for (PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.FINAL)) continue;
        final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod((field).getContainingClass());
        if (setUpMethod != null) {
          for (PsiReference reference: ReferencesSearch.search(field, new LocalSearchScope(setUpMethod))) {
            PsiElement element = reference.getElement();
            if (element instanceof PsiExpression && !PsiUtil.isAccessedForWriting((PsiExpression)element)) {
              continue nextField;
            }
          }
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public abstract BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace();
  protected abstract void initializeInitializerPlace(PsiExpression initializerExpression,
                                                     BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace);
  protected abstract JComponent createInitializerPlacePanel(ItemListener itemListener, ItemListener finalUpdater);
  public abstract void setInitializeInFieldDeclaration();

  public abstract String getFieldVisibility();

  protected void initializeControls(PsiExpression initializerExpression,
                                    BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    myCbFinal.setSelected(myCbFinal.isEnabled() && ourLastCbFinalState);
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

    ItemListener itemListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myCbReplaceAll != null && myAllowInitInMethod) {
          updateInitializerSelection();
        }
        if (shouldUpdateTypeSelector()) {
          updateTypeSelector();
        }
      }
    };
    ItemListener finalUpdater = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateCbFinal();
      }
    };
    final JComponent initializerPlacePanel = createInitializerPlacePanel(itemListener, finalUpdater);
    final JPanel checkboxes = appendCheckboxes(itemListener);
    JPanel panel = composeWholePanel(initializerPlacePanel, checkboxes);

    updateTypeSelector();
    updateCbFinal();
    return panel;
  }

  protected abstract JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel);

  protected void updateInitializerSelection() {
  }

  protected boolean shouldUpdateTypeSelector() {
    return true;
  }

  protected JPanel appendCheckboxes(ItemListener itemListener) {
    GridBagConstraints gbConstraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                              JBInsets.emptyInsets(), 0, 0);
    JPanel panel = new JPanel(new GridBagLayout());
    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setFocusable(false);
    myCbFinal.setText(JavaRefactoringBundle.message("declare.final"));
    myCbFinal.addItemListener(itemListener);
    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);
    appendOccurrences(itemListener, gbConstraints, panel);

    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = JBUI.insetsLeft(8);
      }
      myCbDeleteVariable = new StateRestoringCheckBox();
      myCbDeleteVariable.setText(JavaRefactoringBundle.message("delete.variable.declaration"));
      panel.add(myCbDeleteVariable, gbConstraints);
      if (myIsInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      } else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
                new ItemListener() {
                  @Override
                  public void itemStateChanged(ItemEvent e) {
                    updateCbDeleteVariable();
                  }
                }
        );
      }
    }
    return panel;
  }

  public void appendOccurrences(ItemListener itemListener, GridBagConstraints gbConstraints, JPanel panel) {
    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(JavaRefactoringBundle.message("replace.all.occurrences.of.expression.0.occurrences", myOccurrencesCount));
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
      if (myIsInvokedOnDeclaration) {
        myCbReplaceAll.setEnabled(false);
        myCbReplaceAll.setSelected(true);
      }
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurrences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurrences(false);
    }
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  protected void updateCbFinal() {
    if (!allowFinal()) {
      myCbFinal.makeUnselectable(false);
    } else {
      myCbFinal.makeSelectable();
    }
  }

  protected boolean allowFinal() {
    if (myHasWriteAccess && isReplaceAllOccurrences()) return false;
    return true;
  }

  public void addOccurrenceListener(ItemListener itemListener) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.addItemListener(itemListener);
    }
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.setSelected(replaceAllOccurrences);
    }
  }

  @TestOnly
  public void setCreateFinal(boolean createFinal) {
    myCbFinal.setSelected(createFinal);
    myCbFinal.setEnabled(true);
  }

  protected void enableFinal(boolean enable){
    myCbFinal.setEnabled(enable);
  }


  public void saveFinalState() {
    if (myCbFinal != null && myCbFinal.isEnabled()) {
      ourLastCbFinalState = myCbFinal.isSelected();
      PropertiesComponent.getInstance().setValue(INTRODUCE_FIELD_FINAL_CHECKBOX, ourLastCbFinalState, true);
    }
  }

  protected abstract boolean updateInitializationPlaceModel(boolean initializedInsetup, boolean initializedInConstructor, boolean locals);

  protected abstract boolean hasSetUpChoice();
}
