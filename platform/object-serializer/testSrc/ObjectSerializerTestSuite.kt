// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.assertions.CleanupSnapshots
import com.intellij.util.serialization.ObjectSerializer
import com.intellij.util.serialization.SerializationFilter
import com.intellij.util.serialization.WriteConfiguration
import org.junit.ClassRule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.nio.file.Paths

internal val testSnapshotDir = Paths.get(PlatformTestUtil.getCommunityPath(), "platform/object-serializer/testSnapshots")

internal val objectSerializer
  get() = ObjectSerializer.instance

@RunWith(Suite::class)
@Suite.SuiteClasses(ObjectSerializerTest::class,
                    NonDefaultConstructorTest::class)
class ObjectSerializerTestSuite {
  companion object {
    @ClassRule
    @JvmField
    val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }
}

internal fun test(bean: Any, testName: TestName): String {
  val out = BufferExposingByteArrayOutputStream(8 * 1024)

  // just to test binary
  objectSerializer.write(bean, out, WriteConfiguration(binary = true))
  Assertions.assertThat(out.size() > 0)
  out.reset()

  val writeConfiguration = WriteConfiguration(binary = false, filter = FILTER)
  objectSerializer.write(bean, out, writeConfiguration)

  val ionText = out.toString()
  out.reset()

  val deserializedBean = objectSerializer.read(bean.javaClass, ionText)
  objectSerializer.write(deserializedBean, out, writeConfiguration)
  Assertions.assertThat(out.toString()).isEqualTo(ionText)

  val result = if (SystemInfoRt.isWindows) StringUtilRt.convertLineSeparators(ionText.trim()) else ionText.trim()
  Assertions.assertThat(result).toMatchSnapshot(testSnapshotDir.resolve(FileUtil.sanitizeFileName(testName.methodName) + ".ion"))
  return result
}

// for all our test beans null it is default value - to reduce snapshots, filter null out
private val FILTER = object : SerializationFilter {
  override fun isSkipped(value: Any?): Boolean {
    return value == null || (value is Collection<*> && value.isEmpty())
  }
}