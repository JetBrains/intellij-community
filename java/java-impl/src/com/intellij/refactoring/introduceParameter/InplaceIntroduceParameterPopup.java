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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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

  private final JPanel myWholePanel;
  private int myParameterIndex = -1;
  private InplaceIntroduceParameterUI myPanel;


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
    super(project, editor, expr, localVar, occurrences, typeSelectorManager, IntroduceParameterHandler.REFACTORING_NAME
    );
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myMustBeFinal = mustBeFinal;

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);
    myWholePanel.add(myLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                                             new Insets(0,5,0,5), 0,0));
    myPanel = new InplaceIntroduceParameterUI(project, localVar, expr, method, parametersToRemove, typeSelectorManager,
                                              myEditor, myOccurrences, classMemberRefs, myMustBeFinal) {
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
    };
    myPanel.appendOccurrencesDelegate(myWholePanel);
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(final String[] names, final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiParameter>() {
      @Override
      public PsiParameter compute() {
        final String name = getInputName() != null ? getInputName() : names[0];
        final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
        final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
          .addAfter(elementFactory.createParameter(name, defaultType), anchor);
        PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, myPanel.hasFinalModifier());
        myParameterIndex = myMethod.getParameterList().getParameterIndex(psiParameter);
        return psiParameter;
      }
    });
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
  protected void saveSettings(PsiVariable psiVariable) {
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
    final Runnable runnable = new Runnable() {
      public void run() {
        final Runnable performRefactoring = new Runnable() {
          public void run() {
            final boolean[] conflictsFound = new boolean[]{true};
            processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
              @Override
              public void run() {
                conflictsFound[0] = processor.hasConflicts();
              }
            });
            processor.run();
            normalizeParameterIdxAccordingToRemovedParams(parametersToRemove);
            InplaceIntroduceParameterPopup.super.moveOffsetAfter(!conflictsFound[0]);
            final PsiParameter parameter = getParameter();
            if (parameter != null) {
              InplaceIntroduceParameterPopup.super.saveSettings(parameter);
            }
          }
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          performRefactoring.run();
        } else {
          ApplicationManager.getApplication().invokeLater(performRefactoring);
        }
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), null);
  }

  public boolean isGenerateDelegate() {
    return myPanel.isGenerateDelegate();
  }

  @Override
  protected void updateTitle(PsiVariable variable) {
    updateTitle(variable, variable.getName());
  }

  @Override
  protected void updateTitle(PsiVariable variable, String value) {
    final PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)declarationScope;
      myLabel.clear();
      myLabel.append(psiMethod.getName() + " (", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      boolean frst = true;
      for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
        if (frst) {
          frst = false;
        } else {
          myLabel.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        final SimpleTextAttributes attr = variable == parameter ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES :
                                            myPanel.isParamToRemove(parameter) ?
                                              new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, UIUtil.getInactiveTextColor()) :
                                              SimpleTextAttributes.GRAYED_ATTRIBUTES;
        myLabel.append(parameter.getType().getPresentableText() + " ", attr);
        myLabel.append(variable == parameter ? value : parameter.getName(), attr);
      }
      myLabel.append(")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  @Override
  protected String getActionName() {
    return "IntroduceParameter";
  }

  public String getCommandName() {
    return IntroduceParameterHandler.REFACTORING_NAME;
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
}
