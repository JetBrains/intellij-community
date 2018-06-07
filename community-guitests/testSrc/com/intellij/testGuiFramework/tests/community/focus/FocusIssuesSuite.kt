// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.framework.FirstStartWith
import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.CommunityIdeFirstStart
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(GuiTestSuiteRunner::class)
@RunWithIde(CommunityIde::class)
@FirstStartWith(CommunityIdeFirstStart::class)
@Suite.SuiteClasses(CompletionFocusTest::class,
//                    TypeAheadTest::class,
                    GoToClassFocusTest::class,
                    GoToClassTwiceFocusTest::class,
                    InlineVariableFocusTest::class,
                    KeyMapSettingsTestFocusTest::class,
                    SearchEverywhereFocusTest::class,
                    ReplaceInPathFocusTest::class)
class FocusIssuesSuite