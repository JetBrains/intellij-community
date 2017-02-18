/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.Processor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 3/16/11
 */
public abstract class IntroduceFieldCentralPanel {
   protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceFieldDialog");

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

  protected boolean setEnabledInitializationPlaces(@NotNull final PsiElement initializer) {
    final Set<PsiField> fields = new HashSet<>();
    final Ref<Boolean> refsLocal = new Ref<>(false);
    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
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
    if (!locals && fields.isEmpty()) {
      return true;
    }
    return updateInitializationPlaceModel(!locals && initializedInSetUp(fields),
                                          !locals && initializedInConstructor(fields));
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
      for (PsiField field : fields) {
        final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod((field).getContainingClass());
        if (setUpMethod != null) {
          final Processor<PsiReference> initializerSearcher = reference -> {
            final PsiElement referenceElement = reference.getElement();
            if (referenceElement instanceof PsiExpression) {
              return !PsiUtil.isAccessedForWriting((PsiExpression)referenceElement);
            }
            return true;
          };
          if (ReferencesSearch.search(field, new LocalSearchScope(setUpMethod)).forEach(initializerSearcher)) {
            return false;
          }
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

  public abstract void setVisibility(String visibility);
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
                                                              JBUI.emptyInsets(), 0, 0);
    JPanel panel = new JPanel(new GridBagLayout());
    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setFocusable(false);
    myCbFinal.setText(RefactoringBundle.message("declare.final"));
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
    }
    return panel;
  }

  public void appendOccurrences(ItemListener itemListener, GridBagConstraints gbConstraints, JPanel panel) {
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

  protected abstract boolean updateInitializationPlaceModel(boolean initializedInsetup, boolean initializedInConstructor);

  protected abstract boolean hasSetUpChoice();
}
