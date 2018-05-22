// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.testGuiFramework.framework.GuiTestSuite
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.ide.projectWizard.kotlin.installKotlinPlugin.CreateSdksGuiTest
import com.intellij.ide.projectWizard.kotlin.installKotlinPlugin.InstallPluginGuiTest
import com.intellij.testGuiFramework.framework.FirstStartWith
import com.intellij.testGuiFramework.launcher.ide.CommunityIdeFirstStart
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    KotlinCreateProjectGuiTestSuite.PreparationSteps::class
  , KotlinCreateProjectGuiTestSuite.KotlinCreateProject::class
)
class KotlinCreateProjectGuiTestSuite{

  @RunWith(GuiTestSuite::class)
  @RunWithIde(CommunityIde::class)
   @FirstStartWith(CommunityIdeFirstStart::class)
  @Suite.SuiteClasses(
        InstallPluginGuiTest::class
      , CreateSdksGuiTest::class
  )
  class PreparationSteps

  @RunWith(Suite::class)
  @RunWithIde(CommunityIde::class)
  @Suite.SuiteClasses(
     CreateKotlinProjectGuiTest::class
    , CreateGradleProjectAndConfigureKotlinGuiTest::class
    , CreateGradleProjectAndConfigureOldKotlinGuiTest::class
    , CreateGradleProjectWithKotlinGuiTest::class
    , CreateGradleKotlinDslProjectAndConfigureKotlinGuiTest::class
    , CreateGradleKotlinDslProjectWithKotlinGuiTest::class
    , CreateKotlinMPProjectGuiTest::class
    , CreateJavaProjectAndConfigureKotlinGuiTest::class
    , CreateJavaProjectWithKotlinGuiTest::class // attempt to find a workaround to failing java_with_jvm test when it runs first ever time
    , CreateMavenProjectWithKotlinGuiTest::class
    , CreateMavenProjectAndConfigureKotlinGuiTest::class
  )
  class KotlinCreateProject
}
