// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.ide.impl.HeadlessDataManager.fallbackToProductionDataManager
import com.intellij.lang.fakeLang.FakeFile
import com.intellij.lang.fakeLang.FakeLanguage
import com.intellij.lang.fakeLang.registerFakeLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class SearchTargetTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()

    // use production data manager!
    fallbackToProductionDataManager(testRootDisposable)

    registerFakeLanguage(testRootDisposable)
    registerTestSymbolAndSearchTarget(testRootDisposable, FakeLanguage.id, FakeFile::class.java.name)
  }

  fun testSearchTarget() {
    val fileName = getTestName(false) + ".fake"

    myFixture.configureByText(fileName, """
       search <caret>targets must work, targets are important
    """)

    val results = myFixture.testFindUsagesUsingAction(fileName)
    assertEquals(2, results.size)
  }
}

