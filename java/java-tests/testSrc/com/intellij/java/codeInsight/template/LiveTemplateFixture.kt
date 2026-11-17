// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * The fixture of a live template test. Create it with [liveTemplateFixture].
 *
 * The members start a template in the editor of [fixture] and give the state of the active template.
 */
@TestOnly
class LiveTemplateFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Returns the state of the active template in the editor, or `null` when no template is active or no editor is open.
   */
  @RequiresEdt
  fun getState(): TemplateState? = fixture.editor?.let { TemplateManagerImpl.getTemplateState(it) }

  /**
   * Returns the template manager of the project.
   */
  fun getTemplateManager(): TemplateManagerImpl = TemplateManager.getInstance(fixture.project) as TemplateManagerImpl

  /**
   * Starts [template] in the editor and dispatches the pending events.
   */
  @RequiresEdt
  fun startTemplate(template: Template) {
    TemplateManager.getInstance(fixture.project).startTemplate(fixture.editor, template)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  /**
   * Starts the template [name] of [group] in the editor, as [startTemplate] does.
   */
  @RequiresEdt
  fun startTemplate(name: String, group: String) {
    startTemplate(TemplateSettings.getInstance().getTemplate(name, group)!!)
  }
}

/**
 * Creates a [LiveTemplateFixture] for one test. Declare it as an instance field.
 *
 * The fixture turns on the template testing mode for the test.
 * After the test, it sets the completion case sensitivity back to [CodeInsightSettings.FIRST_LETTER],
 * turns off the selection of autopopup suggestions by characters, and moves the active template to its end.
 *
 * [testDataPath] is the absolute test data path. When it is `null`, the fixture keeps the default path of [lightJavaCodeInsightFixture].
 */
@TestOnly
@JvmOverloads
fun liveTemplateFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<LiveTemplateFixture> = testFixture("liveTemplateFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  TemplateManagerImpl.setTemplateTesting(fixture.testRootDisposable)
  val liveTemplate = LiveTemplateFixture(fixture)
  initialized(liveTemplate) {
    CodeInsightSettings.getInstance().completionCaseSensitive = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars = false
    withContext(Dispatchers.EDT) {
      val state = liveTemplate.getState()
      if (state != null) {
        WriteCommandAction.runWriteCommandAction(fixture.project) { state.gotoEnd() }
      }
    }
  }
}
