// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model

import com.intellij.openapi.util.Bitness
import com.intellij.util.SystemProperties
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.Test

class JdkVersionDetectorTest {
  @Test fun detectJdkVersion() {
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(SystemProperties.getJavaHome())
    assertThat(jdkVersion?.version?.feature).isEqualTo(JavaVersion.current().feature)
    assertThat(jdkVersion?.version?.minor).isEqualTo(JavaVersion.current().minor)
    assertThat(jdkVersion?.version?.update).isEqualTo(JavaVersion.current().update)
    assertThat(jdkVersion?.bitness).isEqualTo(if (CpuArch.is32Bit()) Bitness.x32 else Bitness.x64)
  }
}
