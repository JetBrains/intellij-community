// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.impl.GuiTestCaseExt
import org.junit.Before

open class KotlinGuiTestCase : GuiTestCaseExt() {

  @Before
  override fun setUp() {
    super.setUp()
    KotlinTestProperties.useKotlinArtifactFromEnvironment()
  }

}
