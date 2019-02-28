// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.dtrace.GuiDTTestSuiteRunner
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.tests.community.completionPopup.PopupWindowLeakTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(GuiDTTestSuiteRunner::class)
@RunWithIde(CommunityIde::class)
@Suite.SuiteClasses(PopupWindowLeakTest::class)
class CommunityDTTestSuite