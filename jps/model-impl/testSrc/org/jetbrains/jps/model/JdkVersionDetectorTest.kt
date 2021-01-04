// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model

import com.intellij.openapi.util.Bitness
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
    assertThat(jdkVersion?.bitness).isEqualTo(if (CpuArch.is32Bit()) Bitness.x32 else Bitness.x64)

    if (SystemInfo.isMac && jdkHome.endsWith("/Contents/Home")) {
      val altHome = jdkHome.substring(0, jdkHome.length - "/Contents/Home".length)
      val altVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(altHome)
      assertThat(altVersion?.version).isNull()
    }
  }

  private fun JavaVersion.trim(): JavaVersion = JavaVersion.compose(feature, minor, update, 0, false)
}
