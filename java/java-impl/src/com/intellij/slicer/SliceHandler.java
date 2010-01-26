/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class SliceHandler implements CodeInsightActionHandler {
  private final boolean myDataFlowToThis;

  public SliceHandler(boolean dataFlowToThis) {
    myDataFlowToThis = dataFlowToThis;
  }

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation
    PsiElement expression = getExpressionAtCaret(editor, file);
    if (expression == null) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find what to analyze. Please stand on the expression or variable or method parameter and try again.");
      return;
    }

    SliceManager sliceManager = SliceManager.getInstance(project);
    sliceManager.slice(expression,myDataFlowToThis, this);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public PsiElement getExpressionAtCaret(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset == 0) {
      return null;
    }
    PsiElement atCaret = file.findElementAt(offset);

    PsiElement element = PsiTreeUtil.getParentOfType(atCaret, PsiExpression.class, PsiVariable.class);
    if (myDataFlowToThis && element instanceof PsiLiteralExpression) return null;
    return element;
  }

  public SliceAnalysisParams askForParams(PsiElement element, boolean dataFlowToThis, SliceManager.StoredSettingsBean storedSettingsBean, String dialogTitle) {
    AnalysisScope analysisScope = new AnalysisScope(element.getContainingFile());
    Module module = ModuleUtil.findModuleForPsiElement(element);
    String name = module == null ? null : module.getName();

    Project myProject = element.getProject();
    AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
    analysisUIOptions.save(storedSettingsBean.analysisUIOptions);

    BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(dialogTitle, "Analyze scope", myProject, analysisScope, name, true, analysisUIOptions,
                                                                   element);
    dialog.show();
    if (!dialog.isOK()) return null;

    storedSettingsBean.analysisUIOptions.save(analysisUIOptions);
    
    AnalysisScope scope = dialog.getScope(analysisUIOptions, analysisScope, myProject, module);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = scope;
    params.dataFlowToThis = dataFlowToThis;
    return params;
  }
}
