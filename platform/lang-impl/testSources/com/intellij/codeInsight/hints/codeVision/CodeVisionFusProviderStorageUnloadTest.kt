// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class CodeVisionFusProviderStorageUnloadTest {
  val projectFixture = projectFixture()
  val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("foo", "")
  val editor by psiFileFixture.editorFixture()

  @Test
  fun `a group unload drops the provider storage of an open editor`() {
    editor.putUserData(CodeVisionFusCollector.PROVIDER_STORAGE_KEY, CodeVisionFusProviderStorage(PlainTextLanguage.INSTANCE))

    CodeVisionPluginListener().pluginsUnloaded()

    assertNull(editor.getUserData(CodeVisionFusCollector.PROVIDER_STORAGE_KEY))
  }
}
