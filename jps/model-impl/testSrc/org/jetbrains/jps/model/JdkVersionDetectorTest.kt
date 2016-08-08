/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model

import com.intellij.openapi.util.Bitness
import com.intellij.openapi.util.SystemInfo
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.After
import org.junit.Test
import java.util.concurrent.Executors

class JdkVersionDetectorTest {
  private val service = Executors.newFixedThreadPool(3)

  @After fun tearDown() = service.shutdown()

  @Test fun detectJdkVersion() {
    val jdkHome = System.getProperty("java.home")
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersion(jdkHome, { service.submit(it) })
    assertThat(jdkVersion).contains("\"${SystemInfo.JAVA_VERSION}\"")
  }

  @Test fun detectJdkVersionInfo() {
    val jdkHome = System.getProperty("java.home")
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkHome, { service.submit(it) })
    assertThat(jdkVersion?.version).contains("\"${SystemInfo.JAVA_VERSION}\"")
    assertThat(jdkVersion?.bitness).isEqualTo(if (SystemInfo.is64Bit) Bitness.x64 else Bitness.x32)
  }
}