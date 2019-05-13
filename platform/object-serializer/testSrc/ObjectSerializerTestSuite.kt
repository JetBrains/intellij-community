// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
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
                    NonDefaultConstructorTest::class,
                    MapTest::class,
                    ListTest::class)
class ObjectSerializerTestSuite {
  companion object {
    @ClassRule
    @JvmField
    val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }
}

internal fun <T : Any> test(bean: T, testName: TestName, _writeConfiguration: WriteConfiguration? = null): T {
  val out = BufferExposingByteArrayOutputStream(8 * 1024)

  // just to test binary
  objectSerializer.write(bean, out, WriteConfiguration(binary = true, allowAnySubTypes = _writeConfiguration?.allowAnySubTypes ?: false))
  assertThat(out.size() > 0)
  out.reset()

  val writeConfiguration = _writeConfiguration ?: WriteConfiguration(binary = false, filter = FILTER)
  objectSerializer.write(bean, out, writeConfiguration)

  val ionText = out.toString()
  out.reset()

  val deserializedBean = objectSerializer.read(bean.javaClass, ionText, configuration = ReadConfiguration(allowAnySubTypes = writeConfiguration.allowAnySubTypes))
  objectSerializer.write(deserializedBean, out, writeConfiguration)
  assertThat(out.toString()).isEqualTo(ionText)

  assertThat(ionText.trim()).toMatchSnapshot(testSnapshotDir.resolve(FileUtil.sanitizeFileName(testName.methodName) + ".ion"))
  return deserializedBean
}

// for all our test beans null it is default value - to reduce snapshots, filter null out
internal val FILTER = object : SerializationFilter {
  override fun isSkipped(value: Any?): Boolean {
    return value == null || (value is Collection<*> && value.isEmpty()) || (value is Map<*, *> && value.isEmpty())
  }
}