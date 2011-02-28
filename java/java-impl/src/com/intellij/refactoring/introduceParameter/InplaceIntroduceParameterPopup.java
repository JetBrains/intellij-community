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
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.VariableInplaceIntroducer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageInfo;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 2/25/11
 */
class InplaceIntroduceParameterPopup extends IntroduceParameterSettingsUI {
  private JCheckBox myDelegateCb;

  private Balloon myBalloon;
  private final Project myProject;
  private final Editor myEditor;
  private final TypeSelectorManagerImpl myTypeSelectorManager;
  private final NameSuggestionsGenerator myNameSuggestionsGenerator;
  private final PsiExpression myExpr;
  private final PsiLocalVariable myLocalVar;
  private final PsiMethod myMethod;
  private final PsiMethod myMethodToSearchFor;
  private final PsiExpression[] myOccurrences;
  private final boolean myMustBeFinal;
  private final RangeMarker myExprMarker;
  private final List<RangeMarker> myOccurrenceMarkers;

  private final JPanel myWholePanel;


  InplaceIntroduceParameterPopup(final Project project,
                                 final Editor editor,
                                 final List<UsageInfo> classMemberRefs,
                                 final TypeSelectorManagerImpl typeSelectorManager,
                                 final NameSuggestionsGenerator nameSuggestionsGenerator,
                                 final PsiExpression expr,
                                 final PsiLocalVariable localVar,
                                 final PsiMethod method,
                                 final PsiMethod methodToSearchFor,
                                 final PsiExpression[] occurrences,
                                 final TIntArrayList parametersToRemove,
                                 final boolean mustBeFinal) {
    super(project, localVar, expr, method, parametersToRemove);
    myProject = project;
    myEditor = editor;
    myTypeSelectorManager = typeSelectorManager;
    myNameSuggestionsGenerator = nameSuggestionsGenerator;
    myExpr = expr;
    myLocalVar = localVar;
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myOccurrences = occurrences;
    myMustBeFinal = mustBeFinal;
    myExprMarker = expr != null ? myEditor.getDocument().createRangeMarker(myExpr.getTextRange()) : null;
    myOccurrenceMarkers = new ArrayList<RangeMarker>();

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(BorderFactory.createTitledBorder(IntroduceParameterHandler.REFACTORING_NAME));
    myDelegateCb = new NonFocusableCheckBox(RefactoringBundle.message("delegation.panel.delegate.via.overloading.method"));
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 0), 0, 0);
    myWholePanel.add(myDelegateCb, gc);
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    createLocalVariablePanel(gc, myWholePanel, settings);
    createRemoveParamsPanel(gc, myWholePanel);
    if (Util.anyFieldsWithGettersPresent(classMemberRefs)) {
      gc.gridy++;
      myWholePanel.add(createReplaceFieldsWithGettersPanel(), gc);
    }
  }


  void inplaceIntroduceParameter() {
    final LinkedHashMap<OccurrencesChooser.ReplaceChoice, PsiExpression[]> occurrencesMap =
      new LinkedHashMap<OccurrencesChooser.ReplaceChoice, PsiExpression[]>();

    for (PsiExpression occurrence : myOccurrences) {
      myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
    }

    OccurrencesChooser.fillChoices(myExpr, myOccurrences, occurrencesMap);
    new OccurrencesChooser(myEditor).showChooser(new IntroduceParameterPass(), occurrencesMap);
  }

  @Override
  protected void updateControls(JCheckBox[] removeParamsCb) {
  }

  private class ParameterInplaceIntroducer extends VariableInplaceIntroducer {
    private String myParameterName;
    private SmartTypePointer myParameterTypePointer;
    private SmartTypePointer myDefaultParameterTypePointer;
    private final PsiParameter myParameter;
    private int myParameterIndex;

    private PsiExpression myExpression;
    private final OccurrencesChooser.ReplaceChoice myReplaceChoice;
    private boolean myFinal;
    private final String myExprText;

    public ParameterInplaceIntroducer(PsiParameter parameter,
                                      OccurrencesChooser.ReplaceChoice replaceChoice) {
      super(myProject, new TypeExpression(myProject, myTypeSelectorManager.getTypesForAll()),
            myEditor, parameter, myMustBeFinal,
            myTypeSelectorManager.getTypesForAll().length > 1, myExprMarker, myOccurrenceMarkers);
      myParameter = parameter;
      myReplaceChoice = replaceChoice;
      myExprText = myExpr.getText();
      myParameterIndex = myMethod.getParameterList().getParameterIndex(myParameter);
      myDefaultParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(parameter.getType());
    }

    @Override
    protected PsiVariable getVariable() {
      return myMethod.getParameterList().getParameters()[myParameterIndex];
    }

    @Override
    protected void addReferenceAtCaret(Collection<PsiReference> refs) {
      final PsiVariable variable = getVariable();
      if (variable != null) {
        for (PsiReference reference : ReferencesSearch.search(variable)) {
          refs.remove(reference);
        }
      }
    }

    @Override
    protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
      return true;
    }

    @Override
    protected void collectAdditionalElementsToRename(boolean processTextOccurrences, List<Pair<PsiElement, TextRange>> stringUsages) {
      for (PsiExpression expression : myOccurrences) {
        stringUsages.add(Pair.<PsiElement, TextRange>create(expression, new TextRange(0, expression.getTextLength())));
      }
    }

    @Override
    protected void collectAdditionalRangesToHighlight(Map<TextRange, TextAttributes> rangesToHighlight,
                                                      Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                      EditorColorsManager colorsManager) {
    }

    @Override
    protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges,
                                 @NotNull Editor editor,
                                 @NotNull Collection<RangeHighlighter> highlighters,
                                 @NotNull HighlightManager highlightManager) {
      final TextAttributes attributes =
        EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      for (RangeMarker marker : myOccurrenceMarkers) {
        final int startOffset = marker.getStartOffset();
        highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + myParameter.getName().length(), attributes, 0, highlighters, null);
      }
      super.addHighlights(ranges, editor, highlighters, highlightManager);
    }

    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      InplaceIntroduceParameterPopup.super.saveSettings(settings);
      settings.INTRODUCE_PARAMETER_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
      TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultParameterTypePointer.getType());
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success) {
        boolean isDeleteLocalVariable = false;

        PsiExpression parameterInitializer = myExpression;
        if (myLocalVar != null) {
          if (isUseInitializer()) {
            parameterInitializer = myLocalVar.getInitializer();
          }
          isDeleteLocalVariable = isDeleteLocalVariable();
        }
        final TIntArrayList parametersToRemove = getParametersToRemove();

        final IntroduceParameterProcessor processor =
          new IntroduceParameterProcessor(myProject, myMethod,
                                          myMethodToSearchFor, parameterInitializer, myExpression,
                                          myLocalVar, isDeleteLocalVariable, myParameterName,
                                          myReplaceChoice == OccurrencesChooser.ReplaceChoice.ALL,
                                          getReplaceFieldsWithGetters(), myMustBeFinal || myFinal, myDelegateCb.isSelected(),
                                          myParameterTypePointer.getType(),
                                          parametersToRemove);
        processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
          @Override
          public void run() {
          }
        });
        processor.run();
        normalizeParameterIdxAccordingToRemovedParams(parametersToRemove);
      }
      super.moveOffsetAfter(success);
    }

    private void normalizeParameterIdxAccordingToRemovedParams(TIntArrayList parametersToRemove) {
      parametersToRemove.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          if (myParameterIndex > value) {
            myParameterIndex--;
          }
          return true;
        }
      });
    }



    @Override
    public void finish() {
      super.finish();
      myBalloon.hide();
      final PsiParameter psiParameter = myMethod.getParameterList().getParameters()[myParameterIndex];
      myParameterName = psiParameter.getName();
      myFinal = psiParameter.hasModifierProperty(PsiModifier.FINAL);
      myParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiParameter.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myMethod.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          myExpression = restoreExpression(containingFile, elementFactory, myExprMarker);
          for (RangeMarker marker : myOccurrenceMarkers) {
            if (marker.getStartOffset() == myExprMarker.getStartOffset()) continue;
            restoreExpression(containingFile, elementFactory, marker);
          }
          if (psiParameter.isValid()) {
            psiParameter.delete();
          }
        }
      });

    }

    @Nullable
    private PsiExpression restoreExpression(PsiFile containingFile, PsiElementFactory elementFactory, RangeMarker marker) {
      final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
      final PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
      if (expression != null) {
        return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(myExprText, myMethod));
      }
      return null;
    }


    public boolean createFinals() {
      return hasFinalModifier();
    }
  }

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  private class IntroduceParameterPass extends Pass<OccurrencesChooser.ReplaceChoice> {

    @Override
    public void pass(final OccurrencesChooser.ReplaceChoice replaceChoice) {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          myTypeSelectorManager.setAllOccurences(replaceChoice != OccurrencesChooser.ReplaceChoice.NO);
          final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
          final String[] names = myNameSuggestionsGenerator.getSuggestedNameInfo(defaultType).names;
          final PsiParameter parameter = createParameterToStartTemplateOn(names, defaultType);
          if (parameter != null) {
            myEditor.getCaretModel().moveToOffset(parameter.getTextOffset());
            showSettingsPopup();
            final VariableInplaceRenamer renamer =
              new ParameterInplaceIntroducer(parameter, replaceChoice);
            renamer.performInplaceRename(false, new LinkedHashSet<String>(Arrays.asList(names)));
          }
        }
      }, IntroduceParameterHandler.REFACTORING_NAME, null);
    }

    private PsiParameter createParameterToStartTemplateOn(final String[] names,
                                                          final PsiType defaultType) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
      return ApplicationManager.getApplication().runWriteAction(new Computable<PsiParameter>() {
        @Override
        public PsiParameter compute() {
          final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
            .addAfter(elementFactory.createParameter(names[0], defaultType),
                      JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod));
          PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, hasFinalModifier());
          return psiParameter;
        }
      });
    }

    private void showSettingsPopup() {
      BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(myWholePanel);
      balloonBuilder.setFadeoutTime(0);
      balloonBuilder.setFillColor(IdeTooltipManager.GRAPHITE_COLOR);
      balloonBuilder.setAnimationCycle(0);
      balloonBuilder.setHideOnClickOutside(false);
      balloonBuilder.setHideOnKeyOutside(false);
      balloonBuilder.setHideOnAction(false);
      balloonBuilder.setCloseButtonEnabled(true);
      final RelativePoint target = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
      final Point screenPoint = target.getScreenPoint();
      myBalloon = balloonBuilder.createBalloon();
      myBalloon
        .show(new RelativePoint(new Point(screenPoint.x, screenPoint.y - myEditor.getLineHeight())), Balloon.Position.above);
    }
  }
}
