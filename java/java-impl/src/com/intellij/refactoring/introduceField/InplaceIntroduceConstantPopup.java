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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/18/11
 */
public class InplaceIntroduceConstantPopup extends AbstractInplaceIntroduceFieldPopup {

  private final String myInitializerText;


  private JCheckBox myReplaceAllCb;

  private JCheckBox myMoveToAnotherClassCb;

  public InplaceIntroduceConstantPopup(Project project,
                                       Editor editor,
                                       PsiClass parentClass,
                                       PsiExpression expr,
                                       PsiLocalVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       PsiElement anchorElement,
                                       PsiElement anchorElementIfAll, OccurrenceManager occurrenceManager) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, IntroduceConstantHandler.REFACTORING_NAME,
          parentClass, anchorElement, occurrenceManager, anchorElementIfAll);

    myInitializerText = getExprText(expr, localVariable);


    GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0);
    myWholePanel.add(getPreviewComponent(), gc);

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
                             JBUI.emptyInsets(), 0, 0);
    myReplaceAllCb = new JCheckBox("Replace all occurrences");
    myReplaceAllCb.setMnemonic('a');
    myReplaceAllCb.setFocusable(false);
    myReplaceAllCb.setVisible(myOccurrences.length > 1);
    right.add(myReplaceAllCb, rgc);

    return right;
  }

  private JPanel createLeftPanel() {
    final JPanel left = new JPanel(new GridBagLayout());
    myMoveToAnotherClassCb =
      new JCheckBox("Move to another class", JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS);
    myMoveToAnotherClassCb.setMnemonic('m');
    myMoveToAnotherClassCb.setFocusable(false);
    left.add(myMoveToAnotherClassCb,
             new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(),
                                    0, 0));
    return left;
  }


  private String getSelectedVisibility() {
    if (myParentClass != null && myParentClass.isInterface()) {
      return PsiModifier.PUBLIC;
    }
    String initialVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (initialVisibility == null) {
      initialVisibility = PsiModifier.PUBLIC;
    }
    return initialVisibility;
  }


  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType psiType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {

        PsiField field = elementFactory.createFieldFromText(
          psiType.getCanonicalText() + " " + (chooseName(names, myParentClass.getLanguage())) + " = " + myInitializerText + ";",
          myParentClass);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
        final String visibility = getSelectedVisibility();
        if (visibility != null) {
          PsiUtil.setModifierProperty(field, visibility, true);
        }
        final PsiElement anchorElementIfAll = getAnchorElementIfAll();
        PsiElement finalAnchorElement;
        for (finalAnchorElement = anchorElementIfAll;
             finalAnchorElement != null && finalAnchorElement.getParent() != myParentClass;
             finalAnchorElement = finalAnchorElement.getParent()) {
        }
        PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? (PsiMember)finalAnchorElement : null;
        field = BaseExpressionToFieldHandler.ConvertToFieldRunnable
          .appendField(myExpr, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, myParentClass, myParentClass, field, anchorMember);
        myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
        return field;
      }
    });
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return IntroduceConstantDialog.createNameSuggestionGenerator(propName, myExpr != null && myExpr.isValid() ? myExpr : null, JavaCodeStyleManager.getInstance(myProject), null,
                                                                 myParentClass)
      .getSuggestedNameInfo(defaultType).names;
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.STATIC_FINAL_FIELD;
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    return myReplaceAllCb.isSelected();
  }

  @Override
  public void setReplaceAllOccurrences(boolean allOccurrences) {
    myReplaceAllCb.setSelected(allOccurrences);
  }

  @Override
  protected void saveSettings(@NotNull PsiVariable psiVariable) {
    super.saveSettings(psiVariable);
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getSelectedVisibility();
  }

  @Override
  protected boolean performRefactoring() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS = myMoveToAnotherClassCb.isSelected();
    if (myMoveToAnotherClassCb.isSelected()) {
      myEditor.putUserData(INTRODUCE_RESTART, true);
      ApplicationManager.getApplication().invokeLater(() -> {
        myEditor.putUserData(ACTIVE_INTRODUCE, this);
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
          myEditor.putUserData(INTRODUCE_RESTART, false);
          myEditor.putUserData(ACTIVE_INTRODUCE, null);
          releaseResources();
          if (myLocalMarker != null) {
            myLocalMarker.dispose();
          }
          if (myExprMarker != null) {
            myExprMarker.dispose();
          }
        }
      });
      return false;
    }
    return super.performRefactoring();
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.extractConstant";
  }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return handler instanceof IntroduceConstantHandler && super.startsOnTheSameElement(handler, element);
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
      protected void run(@NotNull Result result) throws Throwable {
        if (getLocalVariable() != null) {
          final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
            new LocalToFieldHandler.IntroduceFieldRunnable(false, (PsiLocalVariable)getLocalVariable(), myParentClass, settings, true,
                                                           myOccurrences);
          fieldRunnable.run();
        }
        else {
          final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
            new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                    myOccurrences, myOccurrenceManager,
                                                                    getAnchorElementIfAll(), getAnchorElement(), myEditor, myParentClass);
          convertToFieldRunnable.run();
        }
      }
    }.execute();
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
}
