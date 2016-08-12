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
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 2/25/11
 */
public class InplaceIntroduceParameterPopup extends AbstractJavaInplaceIntroducer {
  private static final Logger LOG = Logger.getInstance("#" + InplaceIntroduceParameterPopup.class.getName());

  private final PsiMethod myMethod;
  private final PsiMethod myMethodToSearchFor;
  private final boolean myMustBeFinal;

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
                                 final TIntArrayList parametersToRemove,
                                 final boolean mustBeFinal) {
    super(project, editor, expr, localVar, occurrences, typeSelectorManager, IntroduceParameterHandler.REFACTORING_NAME
    );
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myMustBeFinal = mustBeFinal;

    myWholePanel.add(getPreviewComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                                   JBUI.insets(0, 5), 0, 0));
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

      protected TIntArrayList getParametersToRemove() {
        TIntArrayList parameters = new TIntArrayList();
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
    myPanel.appendOccurrencesDelegate(myWholePanel);
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiParameter>() {
      @Override
      public PsiParameter compute() {
        final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
        final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
          .addAfter(elementFactory.createParameter(chooseName(names, myMethod.getLanguage()), defaultType), anchor);
        PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, myPanel.hasFinalModifier());
        myParameterIndex = myMethod.getParameterList().getParameterIndex(psiParameter);
        return psiParameter;
      }
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
    return myWholePanel;
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    return myPanel.isReplaceAllOccurences();
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

  protected void performIntroduce() {
    boolean isDeleteLocalVariable = false;

    PsiExpression parameterInitializer = myExpr;
    if (getLocalVariable() != null) {
      if (myPanel.isUseInitializer()) {
        parameterInitializer = getLocalVariable().getInitializer();
      }
      isDeleteLocalVariable = myPanel.isDeleteLocalVariable();
    }

    final TIntArrayList parametersToRemove = myPanel.getParametersToRemove();

    final IntroduceParameterProcessor processor =
      new IntroduceParameterProcessor(myProject, myMethod,
                                      myMethodToSearchFor, parameterInitializer, myExpr,
                                      (PsiLocalVariable)getLocalVariable(), isDeleteLocalVariable, getInputName(),
                                      myPanel.isReplaceAllOccurences(),
                                      myPanel.getReplaceFieldsWithGetters(), myMustBeFinal || myPanel.isGenerateFinal(),
                                      isGenerateDelegate(),
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
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        performRefactoring.run();
      } else {
        ApplicationManager.getApplication().invokeLater(performRefactoring);
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), null);
  }

  public boolean isGenerateDelegate() {
    return myPanel.isGenerateDelegate();
  }

  @Override
  protected void updateTitle(@Nullable PsiVariable variable) {
    if (variable == null) return;
    updateTitle(variable, variable.getName());
  }

  @Override
  protected void updateTitle(@Nullable final PsiVariable variable, final String value) {
    final PsiElement declarationScope = variable != null ? ((PsiParameter)variable).getDeclarationScope() : null;
    if (declarationScope instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)declarationScope;
      final StringBuilder buf = new StringBuilder();
      buf.append(psiMethod.getName()).append(" (");
      boolean frst = true;
      final List<TextRange> ranges2Remove = new ArrayList<>();
      TextRange addedRange = null;
      for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
        if (frst) {
          frst = false;
        }
        else {
          buf.append(", ");
        }
        int startOffset = buf.length();
        if (myMustBeFinal || myPanel.isGenerateFinal()) {
          buf.append("final ");
        }
        buf.append(parameter.getType().getPresentableText()).append(" ").append(variable == parameter ? value : parameter.getName());
        int endOffset = buf.length();
        if (variable == parameter) {
          addedRange = new TextRange(startOffset, endOffset);
        }
        else if (myPanel.isParamToRemove(parameter)) {
          ranges2Remove.add(new TextRange(startOffset, endOffset));
        }
      }

      buf.append(")");
      setPreviewText(buf.toString());
      final MarkupModel markupModel = DocumentMarkupModel.forDocument(getPreviewEditor().getDocument(), myProject, true);
      markupModel.removeAllHighlighters();
      for (TextRange textRange : ranges2Remove) {
        markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), 0, getTestAttributesForRemoval(), HighlighterTargetArea.EXACT_RANGE);
      }
      markupModel.addRangeHighlighter(addedRange.getStartOffset(), addedRange.getEndOffset(), 0, getTextAttributesForAdd(), HighlighterTargetArea.EXACT_RANGE);
      revalidate();
    }
  }

  private static TextAttributes getTextAttributesForAdd() {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setEffectType(EffectType.ROUNDED_BOX);
    textAttributes.setEffectColor(JBColor.RED);
    return textAttributes;
  }

  private static TextAttributes getTestAttributesForRemoval() {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setEffectType(EffectType.STRIKEOUT);
    textAttributes.setEffectColor(Color.BLACK);
    return textAttributes;
  }

  @Override
  protected String getActionName() {
    return "IntroduceParameter";
  }

  private void normalizeParameterIdxAccordingToRemovedParams(TIntArrayList parametersToRemove) {
    parametersToRemove.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        if (myParameterIndex >= value) {
          myParameterIndex--;
        }
        return true;
      }
    });
  }

  public void setReplaceAllOccurrences(boolean replaceAll) {
    myPanel.setReplaceAllOccurrences(replaceAll);
  }

  public PsiMethod getMethodToIntroduceParameter() {
    return myMethod;
  }

  public PsiMethod getMethodToSearchFor() {
    return myMethodToSearchFor;
  }
}
