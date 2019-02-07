// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.ReassignVariableUtil;

public class InplaceReassignVariableTest extends AbstractJavaInplaceIntroduceTest {
  @Override
  protected void runTest() throws Throwable {
    doRunTest();
  }

  public void testReassignSimple() {
    doTest();
  }

  public void testReassignWhenVariableWasPutInLoopBody() {
    doTest();
  }

  public void testFilterVariablesWhichMustBeEffectivelyFinal() {
    doTest();
  }

  public void testUndoPositionAfterSpace() {
    doUndoTest();
  }

  private void doTest() {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      invokeRefactoring();
      ReassignVariableUtil.reassign(getEditor());
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
      assertNull(AbstractInplaceIntroducer.getActiveIntroducer(getEditor()));

      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private void doUndoTest() {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      invokeRefactoring();
      ReassignVariableUtil.reassign(getEditor());

      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));

      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
      assertNotNull(textEditor);
      UndoManager.getInstance(getProject()).undo(textEditor);

      checkResultByFile(getBasePath() + name + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  @Override
  protected String getBasePath() {
    return "/refactoring/inplaceIntroduceVariable/";
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new InplaceIntroduceVariableTest.MyIntroduceVariableHandler();
  }
}
