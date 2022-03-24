// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.kotlin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.util.text.VersionComparatorUtil
import org.junit.Assume

object KotlinTester {
  private fun canUseKotlin(): Boolean =
    try {
      Class.forName("org.jetbrains.kotlin.idea.run.KotlinRunConfiguration")
      true
    }
    catch (e: ClassNotFoundException) {
      false
    }

  fun assumeCanUseKotlin() {
    Assume.assumeTrue(
      "Kotlin plugin JARs aren't found in the classpath; build 'KotlinPlugin' artifact.", canUseKotlin())
  }

  fun assumeKotlinPluginVersion(version: String) {
    val actualKotlinVersion = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"))?.version
    Assume.assumeTrue("Kotlin plugin version should be at least $version but was: $actualKotlinVersion",
                      VersionComparatorUtil.compare(version, actualKotlinVersion) <= 0)
  }

  const val KOTLIN_VERSION = "1.3.70"

  const val KT_STD_JDK_8_MAVEN_ID = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$KOTLIN_VERSION"

  const val KT_STD_MAVEN_ID = "org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION"

  fun configureKotlinStdLib(model: ModifiableRootModel) {
    MavenDependencyUtil.addFromMaven(model, KT_STD_MAVEN_ID)
    MavenDependencyUtil.addFromMaven(model, KT_STD_JDK_8_MAVEN_ID)
  }
}

fun DefaultLightProjectDescriptor.withKotlinStdlib(): DefaultLightProjectDescriptor {
  withRepositoryLibrary(KotlinTester.KT_STD_MAVEN_ID)
  withRepositoryLibrary(KotlinTester.KT_STD_JDK_8_MAVEN_ID)
  return this
}