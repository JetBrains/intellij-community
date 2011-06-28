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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.introduceParameter.VisibilityListener;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/18/11
 */
public class InplaceIntroduceConstantPopup extends AbstractJavaInplaceIntroducer {
  private PsiElement myAnchorElement;
  private int myAnchorIdx = -1;
  private PsiElement myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;
  private final OccurenceManager myOccurenceManager;

  private final String myInitializerText;


  private JCheckBox myReplaceAllCb;

  private JCheckBox myMoveToAnotherClassCb;

  private JPanel myWholePanel;
  private final PsiClass myParentClass;

  public InplaceIntroduceConstantPopup(Project project,
                                       Editor editor,
                                       PsiClass parentClass,
                                       PsiExpression expr,
                                       PsiLocalVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       PsiElement anchorElement,
                                       PsiElement anchorElementIfAll, OccurenceManager occurenceManager) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, IntroduceConstantHandler.REFACTORING_NAME);
    myParentClass = parentClass;
    myTypeSelectorManager = typeSelectorManager;
    myAnchorElement = anchorElement;
    myAnchorElementIfAll = anchorElementIfAll;
    for (int i = 0, occurrencesLength = occurrences.length; i < occurrencesLength; i++) {
      PsiExpression occurrence = occurrences[i];
      PsiElement parent = occurrence.getParent();
      if (parent == myAnchorElement) {
        myAnchorIdx = i;
      }
      if (parent == myAnchorElementIfAll) {
        myAnchorIdxIfAll = i;
      }
    }
    myOccurenceManager = occurenceManager;

    myInitializerText = getExprText(expr, localVariable);


    myWholePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    myWholePanel.add(myLabel, gc);

    gc.gridy = 1;
    myWholePanel.add(createRightPanel(), gc);

    gc.gridy = 2;
    myWholePanel.add(createLeftPanel(), gc);

  }

  @Nullable
  private static String getExprText(PsiExpression expr, PsiLocalVariable localVariable) {
    final String exprText = expr != null ? expr.getText() : null;
    if (localVariable != null) {
      final PsiExpression initializer = localVariable.getInitializer();
      return initializer != null ? initializer.getText() : exprText;
    }
    else {
      return exprText;
    }
  }

  private JPanel createRightPanel() {
    final JPanel right = new JPanel(new GridBagLayout());
    final GridBagConstraints rgc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(0, 0, 0, 0), 0, 0);
    myReplaceAllCb = new JCheckBox("Replace all occurrences");
    myReplaceAllCb.setMnemonic('a');
    myReplaceAllCb.setFocusable(false);
    myReplaceAllCb.setVisible(myOccurrences.length > 1);
    right.add(myReplaceAllCb, rgc);

    return right;
  }

  private JPanel createLeftPanel() {
    final JPanel left = new JPanel(new GridBagLayout());
    myMoveToAnotherClassCb = new JCheckBox("Move to another class", JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS);
    myMoveToAnotherClassCb.setMnemonic('m');
    myMoveToAnotherClassCb.setFocusable(false);
    left.add(myMoveToAnotherClassCb,
             new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0),
                                    0, 0));
    return left;
  }


  private String getSelectedVisibility() {

    String initialVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (initialVisibility == null) {
      initialVisibility = PsiModifier.PUBLIC;
    }
    return initialVisibility;
  }


  private RangeMarker myFieldRangeStart;


  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType psiType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {

        PsiField field = elementFactory.createFieldFromText(
          psiType.getCanonicalText() + " " + (getInputName() != null ? getInputName() : names[0]) + " = " + myInitializerText + ";",
          myParentClass);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
        final String visibility = getSelectedVisibility();
        if (visibility != null) {
          PsiUtil.setModifierProperty(field, visibility, true);
        }
        field = BaseExpressionToFieldHandler.ConvertToFieldRunnable
          .appendField(myExpr, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, myParentClass, myParentClass,
                       myAnchorElementIfAll, field);
        myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
        return field;
      }
    });
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return IntroduceConstantDialog.createNameSuggestionGenerator(propName, myExpr, JavaCodeStyleManager.getInstance(myProject), null)
      .getSuggestedNameInfo(defaultType).names;
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    return myReplaceAllCb.isSelected();
  }

  @Override
  protected PsiElement checkLocalScope() {
    return myParentClass;
  }

  @Override
  protected void saveSettings(PsiVariable psiVariable) {
    super.saveSettings(psiVariable);
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getSelectedVisibility();
  }

  @Override
  protected PsiVariable getVariable() {
    if (myFieldRangeStart == null) return null;
    PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
    if (element instanceof PsiWhiteSpace) {
      element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
    }
    return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
  }

  @Override
  protected void performPostIntroduceTasks() {
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS = myMoveToAnotherClassCb.isSelected();
      if (myMoveToAnotherClassCb.isSelected()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myEditor.putUserData(ACTIVE_INTRODUCE, InplaceIntroduceConstantPopup.this);
            try {
              final IntroduceConstantHandler constantHandler = new IntroduceConstantHandler();
              final PsiLocalVariable localVariable = (PsiLocalVariable)getLocalVariable();
              if (localVariable != null) {
                constantHandler.invokeImpl(myProject, localVariable, myEditor);
              }
              else {
                constantHandler.invokeImpl(myProject, myExpr, myEditor);
              }
            }
            finally {
              myEditor.putUserData(ACTIVE_INTRODUCE, null);
            }
          }
        });
        return;
      }
    }
    super.moveOffsetAfter(success);
  }

  @Override
  protected void performIntroduce() {
    final BaseExpressionToFieldHandler.Settings settings =
      new BaseExpressionToFieldHandler.Settings(getInputName(),
                                                getExpr(),
                                                getOccurrences(),
                                                isReplaceAllOccurrences(), true,
                                                true,
                                                BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                getSelectedVisibility(), (PsiLocalVariable)getLocalVariable(),
                                                getType(),
                                                true,
                                                myParentClass, false, false);
    new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
      @Override
      protected void run(Result result) throws Throwable {
        if (getLocalVariable() != null) {
          final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
            new LocalToFieldHandler.IntroduceFieldRunnable(false, (PsiLocalVariable)getLocalVariable(), myParentClass, settings, true, myOccurrences);
          fieldRunnable.run();
        }
        else {
          final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
            new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                    myOccurrences, myOccurenceManager,
                                                                    myAnchorElementIfAll, myAnchorElement, myEditor, myParentClass);
          convertToFieldRunnable.run();
        }
      }
    }.execute();
  }

  @Override
  protected void restoreAnchors() {
    if (myAnchorIdxIfAll != -1 && myOccurrences[myAnchorIdxIfAll] != null) {
      myAnchorElementIfAll = myOccurrences[myAnchorIdxIfAll].getParent();
    }

    if (myAnchorIdx != -1 && myOccurrences[myAnchorIdx] != null) {
      myAnchorElement = myOccurrences[myAnchorIdx].getParent();
    }
  }

  @Override
  protected JComponent getComponent() {
    myReplaceAllCb.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        restartInplaceIntroduceTemplate();
      }
    });

    return myWholePanel;
  }

  @Override
  protected String getActionName() {
    return "IntroduceConstant";
  }

  public String getCommandName() {
    return IntroduceConstantHandler.REFACTORING_NAME;
  }
}
