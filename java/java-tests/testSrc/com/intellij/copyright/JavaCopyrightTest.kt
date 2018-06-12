// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory

class JavaCopyrightTest : LightPlatformCodeInsightFixtureTestCase() {
  fun testMultipleCopyrightsInOneFile() {
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

  fun testMultipleCopyrightsInOneFileOneRemoved() {
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
  private fun updateCopyright() {
    val options = CopyrightProfile()
    options.notice = "Copyright text\ncopyright text"
    options.keyword = "Copyright"
    options.allowReplaceRegexp = "JetBrains"
    val updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(myFixture.project, myFixture.module,
                                                                       myFixture.file, options)
    updateCopyright!!.prepare()
    updateCopyright.complete()
  }
}