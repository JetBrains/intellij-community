/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model

import com.intellij.openapi.util.Bitness
import com.intellij.openapi.util.SystemInfo
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.Test

class JdkVersionDetectorTest {
  @Test fun detectJdkVersion() {
    val jdkHome = System.getProperty("java.home")
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkHome)
    assertThat(jdkVersion?.version.toString()).startsWith(SystemInfo.JAVA_VERSION)
    assertThat(jdkVersion?.bitness).isEqualTo(if (SystemInfo.is64Bit) Bitness.x64 else Bitness.x32)
  }
}