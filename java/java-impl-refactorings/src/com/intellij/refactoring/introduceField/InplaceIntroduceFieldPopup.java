// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class InplaceIntroduceFieldPopup extends AbstractInplaceIntroduceFieldPopup {

  private final boolean myStatic;

  private final IntroduceFieldPopupPanel myIntroduceFieldPanel;


  public InplaceIntroduceFieldPopup(PsiLocalVariable localVariable,
                                    PsiClass parentClass,
                                    boolean aStatic,
                                    boolean currentMethodConstructor, PsiExpression[] occurrences,
                                    PsiExpression initializerExpression,
                                    TypeSelectorManagerImpl typeSelectorManager,
                                    Editor editor,
                                    final boolean allowInitInMethod,
                                    boolean allowInitInMethodIfAll, final PsiElement anchorElement,
                                    final PsiElement anchorElementIfAll,
                                    Project project) {
    super(project, editor, initializerExpression, localVariable, occurrences, typeSelectorManager,
          IntroduceFieldHandler.getRefactoringNameText(), parentClass, anchorElement, anchorElementIfAll);
    myStatic = aStatic;
    myIntroduceFieldPanel =
      new IntroduceFieldPopupPanel(parentClass, initializerExpression, localVariable, currentMethodConstructor, localVariable != null, aStatic,
                               myOccurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);

    final GridBagConstraints constraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             JBInsets.emptyInsets(), 0, 0);
    myWholePanel.add(getPreviewComponent(), constraints);

    final JComponent centerPanel = myIntroduceFieldPanel.createCenterPanel();

    myWholePanel.add(centerPanel, constraints);

    myIntroduceFieldPanel.initializeControls(initializerExpression, IntroduceFieldDialog.ourLastInitializerPlace);
  }

  @Override
  protected void showBalloon() {
    super.showBalloon();
    if (myBalloon != null) {
      myIntroduceFieldPanel.setupSelection(myBalloon);
    }
  }

  @Override
  protected PsiField createFieldToStartTemplateOn(final String[] names,
                                                  final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    final PsiField field = WriteAction.compute(() -> DumbService.getInstance(myProject).computeWithAlternativeResolveEnabled(() -> {
      PsiField field1 = elementFactory.createField(chooseName(names, getParentClass().getLanguage()), defaultType);
      PsiUtil.setModifierProperty(field1, PsiModifier.FINAL, myIntroduceFieldPanel.isDeclareFinal());
      PsiUtil.setModifierProperty(field1, PsiModifier.STATIC, myStatic);
      final String visibility = myIntroduceFieldPanel.getFieldVisibility();
      if (visibility != null) {
        PsiUtil.setModifierProperty(field1, visibility, true);
      }
      field1 = (PsiField)getParentClass().add(field1);
      if (myExprText != null) {
        updateInitializer(elementFactory, field1);
      }
      updateVariable(field1);
      return field1;
    }));
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
    return field;
  }

  @Override
  protected SuggestedNameInfo suggestNames(PsiType defaultType, String propName) {
    PsiExpression expression = myExpr != null && myExpr.isValid() ? myExpr : null;
    PsiLocalVariable variable = (PsiLocalVariable)getLocalVariable();
    return JavaNameSuggestionUtil.suggestFieldName(defaultType, variable, expression, myStatic, getParentClass());
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.FIELD;
  }

  @Override
  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    myIntroduceFieldPanel.setReplaceAllOccurrences(replaceAllOccurrences);
  }

  @Override
  protected void updateTitle(@Nullable PsiVariable variable, String value) {
    if (variable == null || !variable.hasInitializer()) {
      super.updateTitle(variable, value);
    } else {

      final PsiExpression initializer = variable.getInitializer();
      assert initializer != null;
      String text = variable.getText().replace(variable.getName(), value);
      text = text.replace(initializer.getText(), PsiExpressionTrimRenderer.render(initializer));
      setPreviewText(text);
      revalidate();
    }
  }

  @Override
  protected void updateTitle(@Nullable PsiVariable variable) {
    if (variable != null){
      updateTitle(variable, variable.getName());
    }
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.extractField";
  }


  @Override
    public boolean isReplaceAllOccurrences() {
      return myIntroduceFieldPanel.isReplaceAllOccurrences();
    }

    @Override
    protected void saveSettings(@NotNull PsiVariable psiVariable) {
      super.saveSettings(psiVariable);
      JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = myIntroduceFieldPanel.getFieldVisibility();
      myIntroduceFieldPanel.saveFinalState();
    }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return handler instanceof IntroduceFieldHandler && super.startsOnTheSameElement(handler, element);
  }

  @Override
    protected JComponent getComponent() {
      myIntroduceFieldPanel.addOccurrenceListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          restartInplaceIntroduceTemplate();
        }
      });

      return myWholePanel;
    }

  private void updateInitializer(PsiElementFactory elementFactory, PsiField variable) {
    if (variable != null) {
      if (myIntroduceFieldPanel.getInitializerPlace() == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
        variable.setInitializer(elementFactory.createExpressionFromText(myExprText, variable));
      } else {
        variable.setInitializer(null);
      }
    }
  }

  @Override
  protected String getActionName() {
    return "IntroduceField";
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
      return myIntroduceFieldPanel.getInitializerPlace();
    }

    @Override
    protected void performIntroduce() {
      IntroduceFieldDialog.ourLastInitializerPlace = myIntroduceFieldPanel.getInitializerPlace();
      final PsiType forcedType = getType();
      LOG.assertTrue(forcedType == null || forcedType.isValid(), forcedType);
      final BaseExpressionToFieldHandler.Settings settings =
        new BaseExpressionToFieldHandler.Settings(getInputName(),
                                                  getExpr(),
                                                  getOccurrences(),
                                                  myIntroduceFieldPanel.isReplaceAllOccurrences(), myStatic,
                                                  myIntroduceFieldPanel.isDeclareFinal(),
                                                  myIntroduceFieldPanel.getInitializerPlace(),
                                                  myIntroduceFieldPanel.getFieldVisibility(), (PsiLocalVariable)getLocalVariable(),
                                                  forcedType,
                                                  myIntroduceFieldPanel.isDeleteVariable(),
                                                  getParentClass(), false, false);
      performIntroduce(settings);
    }
}
