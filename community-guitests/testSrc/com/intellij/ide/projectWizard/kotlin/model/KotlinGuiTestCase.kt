// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.Before

open class KotlinGuiTestCase : GuiTestCase() {

  @Before
  override fun before() {
    super.before()
    KotlinTestProperties.useKotlinArtifactFromEnvironment()
  }

}
