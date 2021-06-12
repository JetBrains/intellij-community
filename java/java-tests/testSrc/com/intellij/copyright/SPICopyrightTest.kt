// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright

import com.intellij.spi.SPIFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory

class SPICopyrightTest : BasePlatformTestCase() {
  fun testSPICopyright() {
    myFixture.configureByText(SPIFileType.INSTANCE,
                              """
#
# Copyright empty
#
fully.qualified.name
""")

    updateCopyright()
    myFixture.checkResult("""
#
# Copyright text
#
fully.qualified.name
""")
  }

  @Throws(Exception::class)
  private fun updateCopyright() {
    val options = CopyrightProfile()
    options.notice = "Copyright text"
    options.keyword = "Copyright"
    val updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(myFixture.project, myFixture.module,
                                                                       myFixture.file, options)
    updateCopyright!!.prepare()
    updateCopyright.complete()
  }
}