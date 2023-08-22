// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
import com.intellij.util.io.sanitizeFileName
import org.junit.ClassRule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.nio.file.Path

internal val testSnapshotDir = Path.of(PlatformTestUtil.getCommunityPath(), "platform/object-serializer/testSnapshots")

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

internal val defaultTestWriteConfiguration = WriteConfiguration(binary = false)

// don't use serialization filter in tests to make sure that the test is closer to production usage
// (e.g. not null arg was not caught by tests because of null filtration)
internal fun <T : Any> test(bean: T,
                            testName: TestName,
                            writeConfiguration: WriteConfiguration,
                            readConfiguration: ReadConfiguration? = null): T {
  val out = BufferExposingByteArrayOutputStream(8 * 1024)

  // just to test binary
  objectSerializer.write(bean, out, writeConfiguration.copy(binary = true))
  assertThat(out.size()).isGreaterThan(0)
  out.reset()

  objectSerializer.write(bean, out, writeConfiguration)

  val ionText = out.toString()
  out.reset()

  val deserializedBean = objectSerializer.read(objectClass = bean.javaClass,
                                               text = ionText,
                                               configuration = readConfiguration ?: ReadConfiguration(allowAnySubTypes = writeConfiguration.allowAnySubTypes))
  objectSerializer.write(deserializedBean, out, writeConfiguration)
  assertThat(out.toString()).isEqualTo(ionText)

  assertThat(ionText.trim()).toMatchSnapshot(testSnapshotDir.resolve("${sanitizeFileName(testName.methodName)}.ion"))
  return deserializedBean
}