// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait

/**
 * The fixture of the Java folding tests. Create it with [javaFoldingFixture].
 *
 * The fixture restores the Java folding settings after the test.
 */
class JavaFoldingFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * The Java folding settings. The test can change them, and the fixture restores them after the test.
   */
  val foldingSettings: JavaCodeFoldingSettings
    get() = JavaCodeFoldingSettings.getInstance()

  /**
   * Configures `a.java` with [text], builds the initial folding, and runs the highlighting.
   */
  fun configure(text: String) {
    configure("a.java", text)
  }

  /**
   * Configures [fileName] with [text], builds the initial folding, and runs the highlighting.
   * The folding and the highlighting run on the EDT.
   */
  fun configure(fileName: String, text: String) {
    fixture.configureByText(fileName, text)
    runInEdtAndWait {
      performInitialFolding(fixture.editor)
      fixture.doHighlighting()
    }
  }

  companion object {
    /**
     * Builds the initial fold regions of [editor]. Call it on the EDT.
     */
    @JvmStatic
    fun performInitialFolding(editor: Editor) {
      EditorTestUtil.buildInitialFoldingsInBackground(editor)
      (editor.foldingModel as FoldingModelEx).rebuild()
    }
  }
}

/**
 * Creates a [JavaFoldingFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
 */
fun javaFoldingFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<JavaFoldingFixture> = testFixture("javaFoldingFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  val settings = JavaCodeFoldingSettings.getInstance() as JavaCodeFoldingSettingsImpl
  val backup = JavaCodeFoldingSettingsImpl()
  backup.loadState(settings)
  initialized(JavaFoldingFixture(fixture)) {
    settings.loadState(backup)
  }
}
