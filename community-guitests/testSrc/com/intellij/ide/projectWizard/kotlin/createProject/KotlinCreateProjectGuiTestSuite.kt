// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.installKotlinPlugin.CreateSdksGuiTest
import com.intellij.ide.projectWizard.kotlin.installKotlinPlugin.InstallPluginGuiTest
import com.intellij.testGuiFramework.framework.FirstStartWith
import com.intellij.testGuiFramework.framework.GuiTestSuite
import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.CommunityIdeFirstStart
import com.intellij.testGuiFramework.testCases.CreateJdkGuiTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * To run Kotlin GuiTestSuites locally you need
 * 1. setup following environment parameters
 *     - `kotlin.gui.test.properties.file` - full path where kotlin.gui.test.properties file(*) should be located
 *     - `kotlin.plugin.download.path` - path to the folder where kotlin plugin will be downloaded to
 *     - `ide.tested` - specifies IDE kind and defines for which IDE Kotlin plugin zip should be downloaded, actual value at the moment is `IJ2018.2`.
 *     - `kotlin.configuration.name` - specifies where Kotlin plugin should be taken from, actual value at the moment is Kotlin_1250_CompilerAllPlugins.
 *        The value is taken from buildTypeId of `Compiler and all IDEA Plugins (...)` configuration on Kotlin TeamCity server.
 *        For testing developed builds specify `Kotlin_dev_CompilerAllPlugins`.
 *     - `kotlin.configuration.buildId` - specifies where Kotlin plugin should be taken from - usual value is `.lastSuccessful`.
 *        Apart it `.lastFinished` can be specified or exact build number like `1710010:id`. Number is taken from the URL
 *        of the corresponding build in the configuration specified by `kotlin.configuration.name`
 *     - `kotlin.artifact.version` - version of Kotlin gradle/maven artifacts,
 *        this parameter should be specified only if its version differs from tested Kotlin IDE plugin.
 *        If this parameter is not specified the value is calculated from Kotlin IDE plugin.
 *     - `kotlin.artifact.isOnlyDevRep` - true, if kotlin-dev repository should be added,
 *        false - everything must work "out of box".
 *        This option is expected true for developed and eap builds what are not published anywhere.
 *        For testing published eap and release builds set this option to false.
 *        If this parameter is not specified it's true
 *     - `kotlin.artifact.isPresentInConfigureDialog` - true, if the tested Kotlin version is present in the Configure Kotlin dialog.
 *        If this parameter is not specified it's true
 *
 * 2. run gradle script: `gradlew -b kotlin.gui.test.configure.gradle` (the script file is located in the one step up folder)
 * (*) as a result of this script working `kotlin.gui.test.properties` file should be created
 * at path specified by `kotlin.gui.test.properties.file` environment variable
 *
 * 3. now the suite can be run
 * */
@RunWith(Suite::class)
@Suite.SuiteClasses(
  PreparationSteps::class
  , KotlinCreateGradleProject::class
)
class KotlinCreateGradleProjectGuiTestSuite : GuiTestSuite()

@RunWith(Suite::class)
@Suite.SuiteClasses(
  PreparationSteps::class
  , KotlinCreateOtherProject::class
)
class KotlinCreateOtherProjectGuiTestSuite : GuiTestSuite()

/**
 * This suite should be run only on final releases - when the tested Kotlin version
 * is downloaded to maven central
 * */
@RunWith(Suite::class)
@Suite.SuiteClasses(
  PreparationSteps::class
  , ConfigureKotlinReleaseTests::class
)
class ConfigureKotlinReleaseGuiTestSuite : GuiTestSuite()


@RunWith(GuiTestSuiteRunner::class)
@RunWithIde(CommunityIde::class)
@FirstStartWith(CommunityIdeFirstStart::class)
@Suite.SuiteClasses(
  InstallPluginGuiTest::class
  , CreateSdksGuiTest::class
  , CreateJdkGuiTest::class
)
class PreparationSteps

@RunWith(Suite::class)
@RunWithIde(CommunityIde::class)
@Suite.SuiteClasses(
  CreateGradleProjectWithKotlinGuiTest::class
  , CreateGradleKotlinDslProjectWithKotlinGuiTest::class
  , CreateKotlinMPProjectGuiTest::class
)
class KotlinCreateGradleProject

@RunWith(Suite::class)
@RunWithIde(CommunityIde::class)
@Suite.SuiteClasses(
  CreateJavaProjectAndConfigureKotlinGuiTest::class
  , CreateKotlinProjectGuiTest::class
  , CreateMavenProjectAndConfigureExactKotlinGuiTest::class
  , CreateGradleProjectAndConfigureExactKotlinGuiTest::class
  , CreateJavaProjectWithKotlinGuiTest::class // attempt to find a workaround to failing java_with_jvm test when it runs first ever time
)
class KotlinCreateOtherProject

@RunWith(Suite::class)
@RunWithIde(CommunityIde::class)
@Suite.SuiteClasses(
  CreateGradleProjectAndConfigureKotlinGuiTest::class
  , CreateGradleKotlinDslProjectAndConfigureKotlinGuiTest::class
  , CreateMavenProjectWithKotlinGuiTest::class
  , CreateMavenProjectAndConfigureKotlinGuiTest::class
)
class ConfigureKotlinReleaseTests

@RunWith(Suite::class)
@Suite.SuiteClasses(
  PreparationSteps::class
  , CreateGradleProjectWithKotlinGuiTest::class
  , CreateGradleKotlinDslProjectWithKotlinGuiTest::class
)
class DebugUIProblemGuiTestSuite : GuiTestSuite()