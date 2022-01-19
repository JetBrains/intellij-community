// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.Test

class JdkVersionDetectorTest {
  @Test fun detectJdkVersion() {
    val jdkHome = SystemProperties.getJavaHome()
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkHome)
    assertThat(jdkVersion?.version?.trim()).isEqualTo(JavaVersion.current().trim())
    assertThat(jdkVersion?.arch).isEqualTo(CpuArch.CURRENT)

    if (SystemInfo.isMac && jdkHome.endsWith("/Contents/Home")) {
      val altHome = jdkHome.substring(0, jdkHome.length - "/Contents/Home".length)
      val altVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(altHome)
      assertThat(altVersion?.version).isNull()
    }
  }

  private fun JavaVersion.trim(): JavaVersion = JavaVersion.compose(feature, minor, update, 0, false)
}
