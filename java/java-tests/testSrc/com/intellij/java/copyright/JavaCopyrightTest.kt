// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.copyright

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class JavaCopyrightTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testMultipleCopyrightsInOneFile(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
/**
* Copyright JetBrains
*/
/**
* Copyright empty
*/
class A {}
""")

    updateCopyright()
    myFixture.checkResult("""
/*
 * Copyright text
 * copyright text
 */

/**
* Copyright empty
*/
class A {}
""")
  }

  @Test
  fun testMultipleCopyrightsInOneFileOneRemoved(): Unit = timeoutRunBlocking {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
/*
 * Copyright JetBrains
 * second line
 */



/*
 * Copyright JetBrains
 */

public class A {}
""")

    updateCopyright()
    myFixture.checkResult("""
/*
 * Copyright text
 * copyright text
 */

public class A {}
""")
  }

  @Throws(Exception::class)
  private suspend fun updateCopyright() {
    val options = CopyrightProfile()
    options.notice = "Copyright text\ncopyright text"
    options.keyword = "Copyright"
    options.allowReplaceRegexp = "JetBrains"
    val updateCopyright = readAction {
      UpdateCopyrightFactory.createUpdateCopyright(myFixture.project, myFixture.module, myFixture.file, options)
    }
    withContext(Dispatchers.EDT) {
      updateCopyright!!.prepare()
      updateCopyright.complete()
    }
  }
}