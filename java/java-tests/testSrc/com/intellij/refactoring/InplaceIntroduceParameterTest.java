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
package com.intellij.refactoring;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceParameter.InplaceIntroduceParameterPopup;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 3/16/11
 */
public class InplaceIntroduceParameterTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/refactoring/inplaceIntroduceParameter/";

  public void testReplaceAll() throws Exception {
    doTest(new Pass<InplaceIntroduceParameterPopup>() {
      @Override
      public void pass(InplaceIntroduceParameterPopup inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  private void doTest(final Pass<InplaceIntroduceParameterPopup> pass) throws Exception {
    String name = getTestName(true);
    configureByFile(BASE_PATH + name + ".java");
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    try {
      templateManager.setTemplateTesting(true);
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final MyIntroduceParameterHandler introduceParameterHandler = new MyIntroduceParameterHandler();
      final PsiExpression expression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
      if (expression != null) {
        introduceParameterHandler.invokeImpl(getProject(), expression, getEditor());
      } else {
        final PsiLocalVariable localVariable =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
        assertNotNull(localVariable);
        introduceParameterHandler.invokeImpl(getProject(), localVariable, getEditor());
      }
      pass.pass(introduceParameterHandler.getInplaceIntroduceParameterPopup());
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(BASE_PATH + name + "_after.java");
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
    }
  }



  public void testEscapePosition() throws Exception {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() throws Exception {
    doTestEscape();
  }

  private void doTestEscape() throws Exception {
    String name = getTestName(true);
    configureByFile(BASE_PATH + name + ".java");
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    try {
      templateManager.setTemplateTesting(true);
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final MyIntroduceParameterHandler introduceParameterHandler = new MyIntroduceParameterHandler();
      final PsiExpression expression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
      if (expression != null) {
        introduceParameterHandler.invokeImpl(getProject(), expression, getEditor());
      } else {
        final PsiLocalVariable localVariable =
          PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
        assertNotNull(localVariable);
        introduceParameterHandler.invokeImpl(getProject(), localVariable, getEditor());
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(true);
      checkResultByFile(BASE_PATH + name + "_after.java");
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
    }
  }


  private static class MyIntroduceParameterHandler extends IntroduceParameterHandler {

    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}
