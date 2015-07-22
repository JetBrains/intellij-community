/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/15/11
 */
public class InplaceIntroduceFieldPopup extends AbstractInplaceIntroduceFieldPopup {

  private final boolean myStatic;

  private final IntroduceFieldPopupPanel myIntroduceFieldPanel;

  static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

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
                                    final OccurrenceManager occurrenceManager, Project project) {
    super(project, editor, initializerExpression, localVariable, occurrences, typeSelectorManager,
          IntroduceFieldHandler.REFACTORING_NAME, parentClass, anchorElement, occurrenceManager, anchorElementIfAll);
    myStatic = aStatic;
    myIntroduceFieldPanel =
      new IntroduceFieldPopupPanel(parentClass, initializerExpression, localVariable, currentMethodConstructor, localVariable != null, aStatic,
                               myOccurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);

    final GridBagConstraints constraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(0, 0, 0, 0), 0, 0);
    myWholePanel.add(getPreviewComponent(), constraints);

    final JComponent centerPanel = myIntroduceFieldPanel.createCenterPanel();

    myWholePanel.add(centerPanel, constraints);

    myIntroduceFieldPanel.initializeControls(initializerExpression, ourLastInitializerPlace);
  }

  protected PsiField createFieldToStartTemplateOn(final String[] names,
                                                final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {
        PsiField field = elementFactory.createField(chooseName(names, myParentClass.getLanguage()), defaultType);
        field = (PsiField)myParentClass.add(field);
        if (myExprText != null) {
          updateInitializer(elementFactory, field);
        }
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, myIntroduceFieldPanel.isDeclareFinal());
        final String visibility = myIntroduceFieldPanel.getFieldVisibility();
        if (visibility != null) {
          PsiUtil.setModifierProperty(field, visibility, true);
        }
         myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
        return field;
      }
    });
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return suggestFieldName(defaultType, (PsiLocalVariable)getLocalVariable(), myExpr != null && myExpr.isValid() ? myExpr : null, myStatic, myParentClass).names;
  }

  public static SuggestedNameInfo suggestFieldName(@Nullable PsiType defaultType,
                                                    @Nullable final PsiLocalVariable localVariable,
                                                    final PsiExpression initializer,
                                                    final boolean forStatic,
                                                    @NotNull final PsiClass parentClass) {
    return IntroduceFieldDialog.
      createGenerator(forStatic, localVariable, initializer, localVariable != null, null, parentClass, parentClass.getProject()).
      getSuggestedNameInfo(defaultType);
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.FIELD;
  }

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

  public void setVisibility(String visibility) {
    myIntroduceFieldPanel.setVisibility(visibility);
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

    protected void performIntroduce() {
      ourLastInitializerPlace = myIntroduceFieldPanel.getInitializerPlace();
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
                                                  myParentClass, false, false);
      new WriteCommandAction(myProject, getCommandName(), getCommandName()){
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          if (getLocalVariable() != null) {
            final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
              new LocalToFieldHandler.IntroduceFieldRunnable(false, (PsiLocalVariable)getLocalVariable(), myParentClass, settings, myStatic, myOccurrences);
            fieldRunnable.run();
          }
          else {
            final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
              new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                      myOccurrences, myOccurrenceManager,
                                                                      getAnchorElementIfAll(),
                                                                      getAnchorElement(), myEditor,
                                                                      myParentClass);
            convertToFieldRunnable.run();
          }
        }
      }.execute();
    }
}
