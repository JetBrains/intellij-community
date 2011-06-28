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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.InplaceIntroduceFieldPopup;
import com.intellij.refactoring.introduceField.IntroduceFieldCentralPanel;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 3/16/11
 */
public class InplaceIntroduceFieldTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/refactoring/inplaceIntroduceField/";

  public void testAnchor() throws Exception {

    doTest(new Pass<InplaceIntroduceFieldPopup>() {
      @Override
      public void pass(InplaceIntroduceFieldPopup inplaceIntroduceFieldPopup) {
      }
    });
  }

  public void testReplaceAll() throws Exception {

    doTest(new Pass<InplaceIntroduceFieldPopup>() {
      @Override
      public void pass(InplaceIntroduceFieldPopup inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
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

      final MyIntroduceFieldHandler introduceFieldHandler = new MyIntroduceFieldHandler();
      final PsiExpression expression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
      if (expression != null) {
        introduceFieldHandler.invokeImpl(getProject(), expression, getEditor());
      } else {
        final PsiLocalVariable localVariable =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
        assertNotNull(localVariable);
        introduceFieldHandler.invokeImpl(getProject(), localVariable, getEditor());
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(true);
      checkResultByFile(BASE_PATH + name + "_after.java");
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
      InplaceIntroduceFieldPopup.setInitializationPlace(null);
    }
  }


  private void doTest(final Pass<InplaceIntroduceFieldPopup> pass) throws Exception {
    String name = getTestName(true);
    configureByFile(BASE_PATH + name + ".java");
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    try {
      templateManager.setTemplateTesting(true);
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final MyIntroduceFieldHandler introduceFieldHandler = new MyIntroduceFieldHandler();
      final PsiExpression expression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
      if (expression != null) {
        introduceFieldHandler.invokeImpl(getProject(), expression, getEditor());
      } else {
        final PsiLocalVariable localVariable =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
        assertNotNull(localVariable);
        introduceFieldHandler.invokeImpl(getProject(), localVariable, getEditor());
      }
      pass.pass(introduceFieldHandler.getInplaceIntroduceFieldPopup());
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(BASE_PATH + name + "_after.java");
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
      InplaceIntroduceFieldPopup.setInitializationPlace(null);
    }
  }

  private static class MyIntroduceFieldHandler extends IntroduceFieldHandler {
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
