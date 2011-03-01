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
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceVariable.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.VariableInplaceIntroducer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
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

  private Balloon myBalloon;
  private final Project myProject;
  private final Editor myEditor;
  private final TypeSelectorManagerImpl myTypeSelectorManager;
  private PsiExpression myExpr;
  private final PsiLocalVariable myLocalVar;
  private final PsiMethod myMethod;
  private final PsiMethod myMethodToSearchFor;
  private final PsiExpression[] myOccurrences;
  private final boolean myMustBeFinal;
  private RangeMarker myExprMarker;
  private List<RangeMarker> myOccurrenceMarkers;

  private final JPanel myWholePanel;
  private int myParameterIndex = -1;
  private String myParameterName;
  private final String myExprText;


  InplaceIntroduceParameterPopup(final Project project,
                                 final Editor editor,
                                 final List<UsageInfo> classMemberRefs,
                                 final TypeSelectorManagerImpl typeSelectorManager,
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
    myExpr = expr;
    myLocalVar = localVar;
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myOccurrences = occurrences;
    myMustBeFinal = mustBeFinal;
    myExprMarker = expr != null ? myEditor.getDocument().createRangeMarker(myExpr.getTextRange()) : null;
    myExprText = myExpr != null ? myExpr.getText() : null;

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(BorderFactory.createTitledBorder(IntroduceParameterHandler.REFACTORING_NAME));
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 0), 0, 0);

    if (myOccurrences.length > 1 && !myIsInvokedOnDeclaration) {
      gc.gridy++;
      createOccurrencesCb(gc, myWholePanel, myOccurrences.length);
    }
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    createLocalVariablePanel(gc, myWholePanel, settings);
    createRemoveParamsPanel(gc, myWholePanel);
    if (Util.anyFieldsWithGettersPresent(classMemberRefs)) {
      gc.gridy++;
      myWholePanel.add(createReplaceFieldsWithGettersPanel(), gc);
    }
    gc.gridy++;
    createDelegateCb(gc, myWholePanel);
  }


  void inplaceIntroduceParameter() {
    new IntroduceParameterPass().pass(OccurrencesChooser.ReplaceChoice.NO);
  }

  @Override
  protected TypeSelectorManager getTypeSelectionManager() {
    return myTypeSelectorManager;
  }

  private PsiParameter getParameter() {
    return myMethod.getParameterList().getParameters()[myParameterIndex];
  }

  public List<RangeMarker> getOccurrenceMarkers() {
    if (myOccurrenceMarkers == null) {
      myOccurrenceMarkers = new ArrayList<RangeMarker>();
      for (PsiExpression occurrence : myOccurrences) {
        myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
      }
    }
    return myOccurrenceMarkers;
  }

  private class ParameterInplaceIntroducer extends VariableInplaceIntroducer {

    private SmartTypePointer myParameterTypePointer;
    private SmartTypePointer myDefaultParameterTypePointer;

    private boolean myFinal;

    public ParameterInplaceIntroducer(PsiParameter parameter) {
      super(myProject, new TypeExpression(myProject, myTypeSelectorManager.getTypesForAll()),
            myEditor, parameter, myMustBeFinal,
            myTypeSelectorManager.getTypesForAll().length > 1, myExprMarker, getOccurrenceMarkers());
      myDefaultParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(parameter.getType());
    }

    @Override
    protected PsiVariable getVariable() {
      return getParameter();
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
      if (isReplaceAllOccurences()) {
        for (PsiExpression expression : myOccurrences) {
          stringUsages.add(Pair.<PsiElement, TextRange>create(expression, new TextRange(0, expression.getTextLength())));
        }
      } else if (myExpr != null){
        stringUsages.add(Pair.<PsiElement, TextRange>create(myExpr, new TextRange(0, myExpr.getTextLength())));
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
      final int variableNameLength = getVariable().getName().length();
      if (isReplaceAllOccurences()) {
        for (RangeMarker marker : getOccurrenceMarkers()) {
          final int startOffset = marker.getStartOffset();
          highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
        }
      } else if (myExpr != null) {
        final int startOffset = myExprMarker.getStartOffset();
        highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
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

        PsiExpression parameterInitializer = myExpr;
        if (myLocalVar != null) {
          if (isUseInitializer()) {
            parameterInitializer = myLocalVar.getInitializer();
          }
          isDeleteLocalVariable = isDeleteLocalVariable();
        }
        final TIntArrayList parametersToRemove = getParametersToRemove();

        final IntroduceParameterProcessor processor =
          new IntroduceParameterProcessor(myProject, myMethod,
                                          myMethodToSearchFor, parameterInitializer, myExpr,
                                          myLocalVar, isDeleteLocalVariable, myParameterName,
                                          isReplaceAllOccurences(),
                                          getReplaceFieldsWithGetters(), myMustBeFinal || myFinal, isGenerateDelegate(),
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
      final PsiParameter psiParameter = (PsiParameter)getVariable();
      LOG.assertTrue(psiParameter != null);
      myFinal = psiParameter.hasModifierProperty(PsiModifier.FINAL);
      myParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiParameter.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myParameterName = psiParameter.getName();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myMethod.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          myExpr = restoreExpression(containingFile, psiParameter, elementFactory, myExprMarker);
          if (myExpr != null) {
            myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
          }
          final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
          for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
            RangeMarker marker = occurrenceMarkers.get(i);
            if (myExprMarker != null && marker.getStartOffset() == myExprMarker.getStartOffset()) {
              myOccurrences[i] = myExpr;
              continue;
            }
            final PsiExpression psiExpression = restoreExpression(containingFile, psiParameter, elementFactory, marker);
            if (psiExpression != null) {
              myOccurrences[i] = psiExpression;
            }
          }
          myOccurrenceMarkers = null;
          if (psiParameter.isValid()) {
            psiParameter.delete();
          }
        }
      });
    }

    @Nullable
    private PsiExpression restoreExpression(PsiFile containingFile,
                                            PsiParameter psiParameter,
                                            PsiElementFactory elementFactory,
                                            RangeMarker marker) {
      if (myExprText == null) return null;
      if (psiParameter == null || !psiParameter.isValid()) return null;
      final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
      final PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
      if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() == psiParameter) {
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

  @Override
  protected void updateControls(JCheckBox[] removeParamsCb) {
    super.updateControls(removeParamsCb);
    if (myParameterIndex < 0) return;
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    if (templateState != null) {
      templateState.gotoEnd(true);
      new IntroduceParameterPass().pass(isReplaceAllOccurences() ? OccurrencesChooser.ReplaceChoice.ALL : OccurrencesChooser.ReplaceChoice.NO);
    }
  }

  private class IntroduceParameterPass extends Pass<OccurrencesChooser.ReplaceChoice> {

    @Override
    public void pass(final OccurrencesChooser.ReplaceChoice replaceChoice) {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          myTypeSelectorManager.setAllOccurences(replaceChoice != OccurrencesChooser.ReplaceChoice.NO);
          final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
          final String propName = myLocalVar != null ? JavaCodeStyleManager
            .getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
          final String[] names = IntroduceParameterHandler.createNameSuggestionGenerator(myExpr, propName)
            .getSuggestedNameInfo(defaultType).names;
          final PsiParameter parameter = createParameterToStartTemplateOn(names, defaultType);
          if (parameter != null) {
            myParameterIndex = myMethod.getParameterList().getParameterIndex(parameter);
            myEditor.getCaretModel().moveToOffset(parameter.getTextOffset());
            showSettingsPopup();
            final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
            nameSuggestions.add(parameter.getName());
            nameSuggestions.addAll(Arrays.asList(names));
            final VariableInplaceRenamer renamer = new ParameterInplaceIntroducer(parameter);
            renamer.performInplaceRename(false, nameSuggestions);
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
          final String name = myParameterName != null ? myParameterName : names[0];
          final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
          final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
            .addAfter(elementFactory.createParameter(name, defaultType), anchor);
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
