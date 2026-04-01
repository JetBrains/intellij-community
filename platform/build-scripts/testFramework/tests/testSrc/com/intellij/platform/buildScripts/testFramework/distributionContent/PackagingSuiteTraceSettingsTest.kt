// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PackagingSuiteTraceSettingsTest {
  @Test
  fun `disable tracing by default`(@TempDir tempDir: Path) {
    withPackagingTracePropertiesCleared {
      val settings = resolvePackagingSuiteTraceSettings(spec(homePath = tempDir), testLogDir = tempDir.resolve("logs"))

      assertThat(settings.enabled).isFalse()
      assertThat(settings.traceFile).isNull()
    }
  }

  @Test
  fun `telemetry enabled property uses default test log file`(@TempDir tempDir: Path) {
    withPackagingTracePropertiesCleared {
      withSystemProperty(PACKAGING_SUITE_TELEMETRY_ENABLED_PROPERTY, "true") {
        val testLogDir = tempDir.resolve("logs")
        val settings = resolvePackagingSuiteTraceSettings(spec(homePath = tempDir), testLogDir = testLogDir)

        assertThat(settings.enabled).isTrue()
        assertThat(settings.traceFile).isEqualTo(testLogDir.resolve("suite-packaging-trace.json"))
      }
    }
  }

  @Test
  fun `trace file property resolves relative path against suite home`(@TempDir tempDir: Path) {
    withPackagingTracePropertiesCleared {
      withSystemProperty(PACKAGING_SUITE_TRACE_FILE_PROPERTY, "traces/suite.json") {
        val settings = resolvePackagingSuiteTraceSettings(spec(homePath = tempDir), testLogDir = tempDir.resolve("logs"))

        assertThat(settings.enabled).isTrue()
        assertThat(settings.traceFile).isEqualTo(tempDir.resolve("traces/suite.json"))
      }
    }
  }

  private fun spec(homePath: Path): PackagingSuiteSpec {
    return PackagingSuiteSpec(
      name = "suite",
      homePath = homePath,
      targets = emptyList(),
    )
  }
}

private const val PACKAGING_SUITE_TELEMETRY_ENABLED_PROPERTY = "intellij.build.test.packaging.telemetry.enabled"
private const val PACKAGING_SUITE_TRACE_FILE_PROPERTY = "intellij.build.test.packaging.trace.file"

private inline fun <T> withPackagingTracePropertiesCleared(action: () -> T): T {
  return withSystemProperty(PACKAGING_SUITE_TELEMETRY_ENABLED_PROPERTY, null) {
    withSystemProperty(PACKAGING_SUITE_TRACE_FILE_PROPERTY, null, action)
  }
}

private inline fun <T> withSystemProperty(name: String, value: String?, action: () -> T): T {
  val previousValue = System.getProperty(name)
  if (value == null) {
    System.clearProperty(name)
  }
  else {
    System.setProperty(name, value)
  }

  return try {
    action()
  }
  finally {
    if (previousValue == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, previousValue)
    }
  }
}
