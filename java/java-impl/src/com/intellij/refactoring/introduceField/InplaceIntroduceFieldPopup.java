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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/15/11
 */
public class InplaceIntroduceFieldPopup extends AbstractJavaInplaceIntroducer {

  private PsiClass myParentClass;
  private boolean myStatic;
  private final Editor myEditor;
  private Project myProject;

  private PsiElement myAnchorElement;
  private int myAnchorIdx = -1;
  private PsiElement myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;
  private final OccurenceManager myOccurenceManager;

  private final IntroduceFieldCentralPanel myIntroduceFieldPanel;

  private JPanel myWholePanel;

  static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;
  private JLabel myLabel = new JLabel("###################");

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
                                    final OccurenceManager occurenceManager, Project project) {
    super(project, editor, initializerExpression, localVariable, occurrences, typeSelectorManager,
          IntroduceFieldHandler.REFACTORING_NAME);
    myParentClass = parentClass;
    myStatic = aStatic;
    myAnchorElement = anchorElement;
    myAnchorElementIfAll = anchorElementIfAll;
    for (int i = 0, occurrencesLength = occurrences.length; i < occurrencesLength; i++) {
      PsiExpression occurrence = occurrences[i];
      PsiElement parent = occurrence.getParent();
      if (parent == myAnchorElement) {
        myAnchorIdx = i;
      }
      if (parent == anchorElementIfAll) {
        myAnchorIdxIfAll = i;
      }
    }
    myOccurenceManager = occurenceManager;
    myProject = myLocalVariable != null ? myLocalVariable.getProject() : initializerExpression.getProject();
    myEditor = editor;

    myIntroduceFieldPanel =
      new IntroduceFieldPopupPanel(parentClass, initializerExpression, localVariable, currentMethodConstructor, localVariable != null, aStatic,
                               myOccurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);

    myWholePanel = new JPanel(new BorderLayout());
    myWholePanel.setBorder(null);
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 0));
    myLabel.setFont(myLabel.getFont().deriveFont(Font.BOLD));
    myWholePanel.add(myLabel, BorderLayout.NORTH);

    final JComponent centerPanel = myIntroduceFieldPanel.createCenterPanel();

    myWholePanel.add(centerPanel, BorderLayout.WEST);

    myIntroduceFieldPanel.initializeControls(initializerExpression, ourLastInitializerPlace);
  }

  @Override
  protected void updateTitle(PsiVariable variable) {
    myLabel.setText(variable.getText());
  }

  protected PsiField createFieldToStartTemplateOn(final String[] names,
                                                final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {
        PsiField field = elementFactory.createField(getInputName() != null ? getInputName() : names[0], defaultType);
        field = (PsiField)myParentClass.add(field);
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
    return IntroduceFieldDialog.createGenerator(myStatic, (PsiLocalVariable)getLocalVariable(), myExpr, getLocalVariable() != null, null)
            .getSuggestedNameInfo(defaultType).names;
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    myIntroduceFieldPanel.setReplaceAllOccurrences(replaceAllOccurrences);
  }

  @TestOnly
  public void setCreateFinal(boolean createFinal) {
    myIntroduceFieldPanel.setCreateFinal(createFinal);
  }

  public void setInitializeInFieldDeclaration() {
    myIntroduceFieldPanel.setInitializeInFieldDeclaration();
  }

  public void setVisibility(String visibility) {
    myIntroduceFieldPanel.setVisibility(visibility);
  }

  public static void setInitializationPlace(BaseExpressionToFieldHandler.InitializationPlace place) {
    ourLastInitializerPlace = place;
  }

    private RangeMarker myFieldRangeStart;


    @Override
    protected boolean isReplaceAllOccurrences() {
      return myIntroduceFieldPanel.isReplaceAllOccurrences();
    }

    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      super.saveSettings(psiVariable);
      JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = myIntroduceFieldPanel.getFieldVisibility();
      myIntroduceFieldPanel.saveFinalState();
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myParentClass;
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

  @Override
  protected String getActionName() {
    return "IntroduceField";
  }

  @Override
    protected String getCommandName() {
      return IntroduceFieldHandler.REFACTORING_NAME;
    }

    @Override
    protected PsiVariable getVariable() {
      if (myFieldRangeStart == null) return null;
      PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
      if (element instanceof PsiWhiteSpace) {
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
      }
      if (element instanceof PsiField) return (PsiVariable)element;
      final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
      if (field != null) return field;
      element = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
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

    protected void performIntroduce() {
      ourLastInitializerPlace = myIntroduceFieldPanel.getInitializerPlace();
      final BaseExpressionToFieldHandler.Settings settings =
        new BaseExpressionToFieldHandler.Settings(getInputName(),
                                                  getExpr(),
                                                  getOccurrences(),
                                                  myIntroduceFieldPanel.isReplaceAllOccurrences(), myStatic,
                                                  myIntroduceFieldPanel.isDeclareFinal(),
                                                  myIntroduceFieldPanel.getInitializerPlace(),
                                                  myIntroduceFieldPanel.getFieldVisibility(), (PsiLocalVariable)getLocalVariable(),
                                                  getType(),
                                                  myIntroduceFieldPanel.isDeleteVariable(),
                                                  myParentClass, false, false);
      new WriteCommandAction(myProject, getCommandName(), getCommandName()){
        @Override
        protected void run(Result result) throws Throwable {
          if (getLocalVariable() != null) {
            final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
              new LocalToFieldHandler.IntroduceFieldRunnable(false, (PsiLocalVariable)getLocalVariable(), myParentClass, settings, myStatic, myOccurrences);
            fieldRunnable.run();
          }
          else {
            final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
              new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                      myOccurrences, myOccurenceManager,
                                                                      myAnchorIdxIfAll != -1 && myOccurrences[myAnchorIdxIfAll] != null ? myOccurrences[myAnchorIdxIfAll].getParent() : myAnchorElementIfAll,
                                                                      myAnchorIdx != -1 && myOccurrences[myAnchorIdx] != null ? myOccurrences[myAnchorIdx].getParent() : myAnchorElement, myEditor,
                                                                      myParentClass);
            convertToFieldRunnable.run();
          }
        }
      }.execute();
    }
}
