// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.hint.EditorCodePreview;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.rename.inplace.SelectableInlayPresentation;
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class InplaceIntroduceParameterPopup extends AbstractJavaInplaceIntroducer {
  private final PsiMethod myMethod;
  private final PsiMethod myMethodToSearchFor;
  private final boolean myMustBeFinal;
  private @NotNull IntroduceVariableBase.JavaReplaceChoice myReplaceChoice;

  private int myParameterIndex = -1;
  private final InplaceIntroduceParameterUI myPanel;


  InplaceIntroduceParameterPopup(final Project project,
                                 final Editor editor,
                                 final TypeSelectorManagerImpl typeSelectorManager,
                                 final PsiExpression expr,
                                 final PsiLocalVariable localVar,
                                 final PsiMethod method,
                                 final PsiMethod methodToSearchFor,
                                 final PsiExpression[] occurrences,
                                 final IntList parametersToRemove,
                                 final boolean mustBeFinal,
                                 final IntroduceVariableBase.@NotNull JavaReplaceChoice replaceChoice) {
    super(project, editor, expr, localVar, occurrences, typeSelectorManager, IntroduceParameterHandler.getRefactoringName()
    );
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myMustBeFinal = mustBeFinal;
    myReplaceChoice = replaceChoice;

    myPanel = new InplaceIntroduceParameterUI(project, localVar, expr, method, parametersToRemove, typeSelectorManager,
                                              myOccurrences) {
      @Override
      protected PsiParameter getParameter() {
        return InplaceIntroduceParameterPopup.this.getParameter();
      }

      @Override
      protected void updateControls(JCheckBox[] removeParamsCb) {
        super.updateControls(removeParamsCb);
        if (myParameterIndex < 0) return;
        restartInplaceIntroduceTemplate();
      }

      @Override
      protected IntList getParametersToRemove() {
        IntList parameters = new IntArrayList();
        if (myCbReplaceAllOccurences == null || myCbReplaceAllOccurences.isSelected()) {
          for (int i = 0; i < myParametersToRemove.length; i++) {
            if (myParametersToRemove[i] != null) {
              parameters.add(i);
            }
          }
        }
        return parameters;
      }
    };
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsLeft(6), 0, 0);
    myPanel.createDelegateCb(gc, myWholePanel);
    gc.insets.top = JBUI.scale(6);
    myWholePanel.add(new LinkLabel<>(LangBundle.message("inlay.rename.link.label.more.options"), null){
      @Override
      public void doClick() {
        new IntroduceParameterHandler().invoke(myProject, myEditor, myMethod.getContainingFile(), DataManager.getInstance().getDataContext(myEditor.getComponent()));
      }
    }, gc);
  }

  @Override
  protected void showDialogAdvertisement(@NonNls String actionId) {
    initPopupOptionsAdvertisement();
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    return WriteAction.compute(() -> {
      final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
      final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
        .addAfter(elementFactory.createParameter(chooseName(names, myMethod.getLanguage()), defaultType), anchor);
      PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, myPanel.hasFinalModifier());
      myParameterIndex = myMethod.getParameterList().getParameterIndex(psiParameter);
      return psiParameter;
    });
  }

  @Override
  protected PsiElement checkLocalScope() {
    return myMethod;
  }

  @Override
  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    return new LocalSearchScope(myMethod);
  }

  @Override
  protected VariableKind getVariableKind() {
    return VariableKind.PARAMETER;
  }

  @Override
  protected String[] suggestNames(PsiType defaultType, String propName) {
    return IntroduceParameterHandler.createNameSuggestionGenerator(myExpr, propName, myProject, null)
      .getSuggestedNameInfo(defaultType).names;
  }


  @Nullable
  private PsiParameter getParameter() {
    if (!myMethod.isValid()) return null;
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    return parameters.length > myParameterIndex && myParameterIndex >= 0 ? parameters[myParameterIndex] : null;
  }


  @Override
  protected JComponent getComponent() {
    return null;
  }

  @Override
  protected void afterTemplateStart() {
     super.afterTemplateStart();
    TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    if (templateState == null) return;

    TextRange currentVariableRange = templateState.getCurrentVariableRange();
    if (currentVariableRange == null) return;
    
    IntroduceParameterUsagesCollector.started.log(IntroduceParameterUsagesCollector.replaceAll.with(myReplaceChoice.isAll()));

    SelectableInlayPresentation presentation = TemplateInlayUtil.createSettingsPresentation((EditorImpl)templateState.getEditor(), 
                                                                                            IntroduceParameterHelperKt.logStatisticsOnShowCallback(myProject));

    TemplateInlayUtil.SelectableTemplateElement templateElement = new TemplateInlayUtil.SelectableTemplateElement(presentation) {
      @Override
      public void onSelect(@NotNull TemplateState templateState) {
        super.onSelect(templateState);
        IntroduceParameterHelperKt.logStatisticsOnShow(null, myProject);
      }
    };
    TemplateInlayUtil.createNavigatableButtonWithPopup(templateState, 
                                                       currentVariableRange.getEndOffset(),
                                                       presentation, 
                                                       myWholePanel, 
                                                       templateElement, 
                                                       IntroduceParameterHelperKt.logStatisticsOnHideCallback(myProject, myPanel::isGenerateDelegate));

    PsiParameter parameter = getParameter();
    if (parameter == null) return;
    
    showPreview(parameter, templateState);
  }

  
  private void showPreview(PsiParameter psiParameter, Disposable parentDisposable) {
    MarkupModel markupModel = myEditor.getMarkupModel();
    TextRange newParameterRange = psiParameter.getTextRange();
    List<RangeHighlighter> highlighters = new ArrayList<>();
    highlighters.add(markupModel.addRangeHighlighter(newParameterRange.getStartOffset(), newParameterRange.getEndOffset(), 0, getTextAttributesForAdd(myEditor), HighlighterTargetArea.EXACT_RANGE));
    PsiParameterList list = myMethod.getParameterList();
    for (PsiParameter parameter : list.getParameters()) {
      if (parameter != psiParameter && myPanel.isParamToRemove(parameter)) {
        TextRange range = parameter.getTextRange();
        highlighters.add(markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), 0, getTestAttributesForRemoval(), HighlighterTargetArea.EXACT_RANGE));
      }
    }
    EditorCodePreview preview = EditorCodePreview.Companion.create(myEditor);
    Document document = myEditor.getDocument();
    
    preview.addPreview(new IntRange(document.getLineNumber(myMethod.getTextOffset()), document.getLineNumber(list.getTextRange().getEndOffset())), 
                       IntroduceParameterHelperKt.onClickCallback(psiParameter));
    Disposer.register(parentDisposable, () -> {
      Disposer.dispose(preview);
      for (RangeHighlighter highlighter : highlighters) {
        highlighter.dispose();
      }
    });
  }
  
  @Override
  public boolean isReplaceAllOccurrences() {
    return myReplaceChoice.isAll();
  }

  @Override
  protected PsiVariable getVariable() {
    return getParameter();
  }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return handler instanceof IntroduceParameterHandler && super.startsOnTheSameElement(handler, element);
  }


  @Override
  protected void saveSettings(@NotNull PsiVariable psiVariable) {
    myPanel.saveSettings(JavaRefactoringSettings.getInstance());
  }

  @Override
  protected void performIntroduce() {
    boolean isDeleteLocalVariable = false;

    PsiExpression parameterInitializer = myExpr;
    if (getLocalVariable() != null) {
      if (myPanel.isUseInitializer()) {
        parameterInitializer = getLocalVariable().getInitializer();
      }
      isDeleteLocalVariable = myPanel.isDeleteLocalVariable();
    }

    final IntList parametersToRemove = myPanel.getParametersToRemove();

    final IntroduceParameterProcessor processor =
      new IntroduceParameterProcessor(myProject, myMethod,
                                      myMethodToSearchFor, parameterInitializer, myExpr,
                                      (PsiLocalVariable)getLocalVariable(), isDeleteLocalVariable, getInputName(),
                                      myReplaceChoice,
                                      myPanel.getReplaceFieldsWithGetters(), myMustBeFinal || myPanel.isGenerateFinal(),
                                      isGenerateDelegate(),
                                      false,
                                      getType(),
                                      parametersToRemove);
    final Runnable runnable = () -> {
      final Runnable performRefactoring = () -> {
        processor.setPrepareSuccessfulSwingThreadCallback(() -> {
        });
        processor.run();
        normalizeParameterIdxAccordingToRemovedParams(parametersToRemove);
        final PsiParameter parameter = getParameter();
        if (parameter != null) {
          super.saveSettings(parameter);
        }
      };
      ApplicationManager.getApplication().invokeLater(performRefactoring, myProject.getDisposed());
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), null);
  }

  public boolean isGenerateDelegate() {
    return myPanel.isGenerateDelegate();
  }

  @NotNull
  public IntroduceVariableBase.JavaReplaceChoice getReplaceChoice() {
    return myReplaceChoice;
  }

  private static TextAttributes getTextAttributesForAdd(Editor editor) {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setBackgroundColor(editor.getColorsScheme().getColor(EditorColors.ADDED_LINES_COLOR));
    return textAttributes;
  }

  private static TextAttributes getTestAttributesForRemoval() {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setEffectType(EffectType.STRIKEOUT);
    textAttributes.setEffectColor(JBColor.BLACK);
    return textAttributes;
  }

  @Override
  protected String getActionName() {
    return "IntroduceParameter";
  }

  private void normalizeParameterIdxAccordingToRemovedParams(IntList parametersToRemove) {
    parametersToRemove.forEach(value -> {
      if (myParameterIndex >= value) {
        myParameterIndex--;
      }
    });
  }

  @Override
  public void setReplaceAllOccurrences(boolean replaceAll) { 
    myReplaceChoice = replaceAll ? IntroduceVariableBase.JavaReplaceChoice.ALL : IntroduceVariableBase.JavaReplaceChoice.NO;
  }

  public PsiMethod getMethodToIntroduceParameter() {
    return myMethod;
  }

  public PsiMethod getMethodToSearchFor() {
    return myMethodToSearchFor;
  }
}
