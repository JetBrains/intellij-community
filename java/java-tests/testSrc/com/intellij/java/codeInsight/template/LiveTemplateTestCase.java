// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;

public abstract class LiveTemplateTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
      CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
      TemplateState state = getState();
      if (state != null) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> state.gotoEnd());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected TemplateState getState() {
    Editor editor = getEditor();
    return editor == null ? null : TemplateManagerImpl.getTemplateState(editor);
  }

  protected TemplateManagerImpl getTemplateManager() {
    return (TemplateManagerImpl)TemplateManager.getInstance(getProject());
  }

  public void startTemplate(Template template) {
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), template);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  public void startTemplate(String name, String group) {
    startTemplate(TemplateSettings.getInstance().getTemplate(name, group));
  }
}
