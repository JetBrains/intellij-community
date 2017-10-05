// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.samples

import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
class JListTest : GuiTestCase() {

  @Test
  fun testJList() {
    welcomeFrame {
      actionLink("Create New Project").click()
      projectWizard {
        jList("Java").clickItem("Java")
        button("Cancel").click()
      }
    }
  }

}
