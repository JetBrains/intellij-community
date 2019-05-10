// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.nio.file.Paths

private val testSnapshotDir = Paths.get(PlatformTestUtil.getCommunityPath(), "platform/object-serializer/testSnapshots")

@RunWith(Suite::class)
@Suite.SuiteClasses(ObjectSerializerTest::class)
class ObjectSerializerTestSuite {
  companion object {
    @ClassRule
    @JvmField
    val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }
}

class ObjectSerializerTest {
  companion object {
    private val objectSerializer = ObjectSerializer()
  }

  private fun test(bean: Any): String {
    val out = BufferExposingByteArrayOutputStream(8 * 1024)

    // just to test binary
    objectSerializer.write(bean, out, binary = true)
    assertThat(out.size() > 0)
    out.reset()

    objectSerializer.write(bean, out, binary = false, filter = FILTER)

    val ionText = out.toString()
    out.reset()

    val deserializedBean = objectSerializer.read(bean.javaClass, ionText)
    objectSerializer.write(deserializedBean, out, binary = false, filter = FILTER)
    assertThat(out.toString()).isEqualTo(ionText)

    val result = if (SystemInfoRt.isWindows) StringUtilRt.convertLineSeparators(ionText.trim()) else ionText.trim()
    assertThat(result).toMatchSnapshot(testSnapshotDir.resolve(sanitizeFileName(testName.methodName) + ".ion"))
    return result
  }

  @Rule
  @JvmField
  val testName = TestName()

  @Test
  fun `int and null string`() {
    test(TestBean())
  }

  @Test
  fun `int and string`() {
    test(TestBean(foo = "bar"))
  }

  @Test
  fun `int as object`() {
    val bean = TestBean()
    bean.intAsObject = 4212
    test(bean)
  }

  @Test
  fun `boolean as null object`() {
    test(TestBoolBean())
  }

  @Test
  fun `boolean as object`() {
    val bean = TestBoolBean()
    bean.boolAsObject = false
    test(bean)
  }

  @Test
  fun `float as null object`() {
    test(TestFloatBean())
  }

  @Test
  fun `float as object`() {
    val bean = TestFloatBean()
    bean.doubleAsObject = 1412.123
    bean.floatAsObject = 54.12f
    test(bean)
  }

  @Test
  fun `self class reference`() {
    test(TestObjectBean())
  }

  @Test
  fun `recursive reference`() {
    val bean = TestObjectBean()
    bean.bean = bean
    test(bean)
  }

  @Test
  fun `several recursive reference`() {
    val bean = TestObjectBean()
    val bean2 = TestObjectBean()
    bean.bean = bean2
    bean2.bean = bean
    test(bean)
  }

  @Test
  fun `list of strings`() {
    val bean = TestObjectBean()
    bean.list.add("foo")
    bean.list.add("bar")
    test(bean)
  }

  @Test
  fun `list of objects`() {
    val bean = TestObjectBean()

    val item1 = TestObjectBean()
    item1.bean = bean
    bean.children.add(item1)
    bean.children.add(TestObjectBean())

    test(bean)
  }

  @Test
  fun `read root list`() {
    val out = BufferExposingByteArrayOutputStream(8 * 1024)

    objectSerializer.writeList(listOf("foo", "bar"), String::class.java, out, binary = false)

    val ionText = out.toString()
    out.reset()

    val result = objectSerializer.readList(String::class.java, ionText.reader())
    assertThat(result).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun `array of string`() {
    test(TestArrayBean(list = arrayOf("foo", "bar")))
  }

  @Test
  fun `array of objects`() {
    test(TestArrayBean(children = arrayOf(TestBean(foo = "foo"), TestBean(foo = "or bar"))))
  }
}

private class TestArrayBean(
  @JvmField var list: Array<String>? = null,
  /*  test set to final field */@JvmField val children: Array<TestBean> = arrayOf()
)

private class TestObjectBean {
  @JvmField
  var bean: TestObjectBean? = null

  @JvmField
  val list = mutableListOf<String>()

  @JvmField
  val children = mutableListOf<TestObjectBean>()
}

@Suppress("unused")
private class TestBean(@JvmField var foo: String? = null) {
  @JvmField
  var short: Short = 4
  @JvmField
  var long = Long.MAX_VALUE
  @JvmField
  var counter = 42

  @JvmField
  var intAsObject: Int? = null
}

@Suppress("unused")
private class TestBoolBean {
  @JvmField
  var boolAsObject: Boolean? = null

  @JvmField
  var bool = false
}

@Suppress("unused")
private class TestFloatBean {
  @JvmField
  var doubleAsObject: Double? = null
  @JvmField
  var floatAsObject: Float? = null

  @JvmField
  var double: Double = 9.2
  @JvmField
  var float: Float = 0.4f
}

// for all our test beans null it is default value - to reduce snapshots, filter null out
private val FILTER = object : SerializationFilter {
  override fun isSkipped(value: Any?): Boolean {
    return value == null || (value is Collection<*> && value.isEmpty())
  }
}