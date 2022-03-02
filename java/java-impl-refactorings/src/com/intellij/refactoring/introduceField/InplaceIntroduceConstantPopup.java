// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class InplaceIntroduceConstantPopup extends AbstractInplaceIntroduceFieldPopup {

  private final String myInitializerText;


  private JCheckBox myReplaceAllCb;

  private JCheckBox myMoveToAnotherClassCb;
  private String myVisibility;

  public InplaceIntroduceConstantPopup(Project project,
                                       Editor editor,
                                       PsiClass parentClass,
                                       PsiExpression expr,
                                       PsiLocalVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       PsiElement anchorElement,
                                       PsiElement anchorElementIfAll, OccurrenceManager occurrenceManager) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, IntroduceConstantHandler.getRefactoringNameText(),
          parentClass, anchorElement, occurrenceManager, anchorElementIfAll);

    myInitializerText = getExprText(expr, localVariable);


    GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(), 0, 0);
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
                             JBInsets.emptyInsets(), 0, 0);
    myReplaceAllCb = new JCheckBox(RefactoringBundle.message("replace.all.occurences.checkbox"));
    myReplaceAllCb.setMnemonic('a');
    myReplaceAllCb.setFocusable(false);
    myReplaceAllCb.setVisible(myOccurrences.length > 1);
    myReplaceAllCb.setSelected(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_REPLACE_ALL);
    right.add(myReplaceAllCb, rgc);

    return right;
  }

  private JPanel createLeftPanel() {
    final JPanel left = new JPanel(new GridBagLayout());
    myMoveToAnotherClassCb = new JCheckBox(JavaRefactoringBundle.message("introduce.constant.move.to.another.class.checkbox"), false);
    myMoveToAnotherClassCb.setMnemonic('m');
    myMoveToAnotherClassCb.setFocusable(false);
    left.add(myMoveToAnotherClassCb,
             new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(),
                                    0, 0));
    return left;
  }


  @NotNull
  private String getSelectedVisibility() {
    if (getParentClass() != null && getParentClass().isInterface()) {
      return PsiModifier.PUBLIC;
    }
    String initialVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (initialVisibility == null) {
      initialVisibility = PsiModifier.PUBLIC;
    }
    else {
      String effectiveVisibility = IntroduceConstantDialog.getEffectiveVisibility(initialVisibility, myOccurrences, getParentClass(), myProject);
      if (effectiveVisibility != null) {
        return effectiveVisibility;
      }
    }
    return initialVisibility;
  }


  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType psiType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return WriteAction.compute(() -> {

      PsiClass parentClass = getParentClass();
      PsiField field = elementFactory.createFieldFromText(
        psiType.getCanonicalText() + " " + (chooseName(names, parentClass.getLanguage())) + " = " + myInitializerText + ";", parentClass);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      myVisibility = getSelectedVisibility();
      PsiUtil.setModifierProperty(field, myVisibility, true);
      PsiElement finalAnchorElement = getAnchorElementIfAll();
      while (finalAnchorElement != null && finalAnchorElement.getParent() != parentClass) {
        finalAnchorElement = finalAnchorElement.getParent();
      }
      PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? (PsiMember)finalAnchorElement : null;
      field = BaseExpressionToFieldHandler.ConvertToFieldRunnable
        .appendField(myExpr, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, parentClass, parentClass, field,
                     anchorMember);
      myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
      return field;
    });
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return IntroduceConstantDialog.createNameSuggestionGenerator(propName, myExpr != null && myExpr.isValid() ? myExpr : null, JavaCodeStyleManager.getInstance(myProject), null,
                                                                 getParentClass())
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
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = myVisibility;
  }

  @Override
  protected boolean performRefactoring() {
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
      }, myProject.getDisposed());
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
  protected boolean startsOnTheSameElements(Editor editor,
                                            RefactoringActionHandler handler,
                                            PsiElement[] elements) {
    if (elements.length == 0 && handler instanceof IntroduceConstantHandler) {
      PsiVariable variable = getVariable();
      if (variable != null) {
        PsiReference reference = TargetElementUtil.findReference(editor);
        if (reference instanceof PsiReferenceExpression &&
            reference.resolve() == null &&
            Comparing.strEqual(variable.getName(), ((PsiReferenceExpression)reference).getReferenceName())) {
          return true;
        }
      }
    }
    return elements.length == 1 && startsOnTheSameElement(handler, elements[0]);
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
                                                myVisibility, (PsiLocalVariable)getLocalVariable(),
                                                getType(),
                                                true,
                                                getParentClass(), false, false);
    if (myReplaceAllCb.isVisible()) {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_REPLACE_ALL = isReplaceAllOccurrences();
    }
    WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).withGroupId(getCommandName()).run(() -> {
      if (getLocalVariable() != null) {
        final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
          new LocalToFieldHandler.IntroduceFieldRunnable(false, (PsiLocalVariable)getLocalVariable(), getParentClass(), settings, myOccurrences);
        fieldRunnable.run();
      }
      else {
        final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
          new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                  myOccurrences, myOccurrenceManager,
                                                                  getAnchorElementIfAll(), getAnchorElement(), myEditor, getParentClass());
        convertToFieldRunnable.run();
      }
    });
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
