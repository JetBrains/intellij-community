// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.IconManager
import com.intellij.ui.icons.CoreIconManager
import com.intellij.util.AstLoadingFilter
import com.intellij.util.PsiIconUtil
import com.intellij.util.ThrowableRunnable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier

@TestApplication
@RegistryKey(key = "ast.loading.filter", value = "true")
class JsonIconsAstLoadingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    IconManager.activate(CoreIconManager())
  }

  @AfterEach
  fun tearDown() {
    IconManager.deactivate()
    IconLoader.clearCacheInTests()
  }

  /**
   * If this test fails for your [com.intellij.ide.IconProvider] you MUST avoid loading PSI by either:
   * - indexing and accessing index instead from [com.intellij.ide.IconProvider];
   * - cached computation on AST, e.g., using [com.intellij.util.gist.GistAstMarker].
   */
  @Test
  fun testNoAstLoadedFromIconProviders(): Unit = timeoutRunBlocking {
    val file = myFixture.addFileToProject("package.json", """
      {
        "name": "my-awesome-package",
        "version": "1.0.0",
        "author": "Your Name <email@example.com>"
      }
    """.trimIndent())

    readAction {
      AstLoadingFilter.disallowTreeLoading(ThrowableRunnable {
        PsiIconUtil.getIconFromProviders(file, 0)
      }, Supplier { "IconProvider must not access PSI of files directly! Use either indexes or GistManager to cache computation" })
    }
  }
}
