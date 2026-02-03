// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.AstLoadingFilter
import com.intellij.util.PsiIconUtil
import com.intellij.util.ThrowableRunnable
import java.util.function.Supplier

class JsonIconsAstLoadingTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()

    Registry.get("ast.loading.filter").setValue(true, testRootDisposable);
  }

  override fun isIconRequired(): Boolean = true

  /**
   * If this test fails for your [com.intellij.ide.IconProvider] you MUST avoid loading PSI by either:
   * - indexing and accessing index instead from [com.intellij.ide.IconProvider];
   * - cached computation on AST, e.g., using [com.intellij.util.gist.GistAstMarker].
   */
  fun testNoAstLoadedFromIconProviders() {
    val file = myFixture.addFileToProject("package.json", """
      {
        "name": "my-awesome-package",
        "version": "1.0.0",
        "author": "Your Name <email@example.com>"
      }
    """.trimIndent())

    AstLoadingFilter.disallowTreeLoading(ThrowableRunnable {
      PsiIconUtil.getIconFromProviders(file, 0)
    }, Supplier { "IconProvider must not access PSI of files directly! Use either indexes or GistManager to cache computation" })
  }
}