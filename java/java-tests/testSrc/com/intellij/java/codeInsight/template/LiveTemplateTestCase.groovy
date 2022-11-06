// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import groovy.transform.CompileStatic

@CompileStatic
abstract class LiveTemplateTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable())
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = false
    if (state != null) {
      WriteCommandAction.runWriteCommandAction project, {
        state.gotoEnd()
      }
    }
    super.tearDown()
  }

  protected TemplateState getState() {
    editor?.with { TemplateManagerImpl.getTemplateState(it) }
  }

  protected TemplateManagerImpl getTemplateManager() {
    return TemplateManager.getInstance(project) as TemplateManagerImpl
  }

  def startTemplate(Template template) {
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), template)
    UIUtil.dispatchAllInvocationEvents()
  }

  def startTemplate(String name, String group) {
    startTemplate(TemplateSettings.getInstance().getTemplate(name, group))
  }
}
