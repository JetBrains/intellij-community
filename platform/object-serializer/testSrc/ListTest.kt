// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.SmartList
import com.intellij.util.io.readChars
import com.intellij.util.io.write
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.ByteArrayOutputStream

class ListTest {
  @Rule
  @JvmField
  val testName = TestName()

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  private fun <T : Any> test(bean: T, writeConfiguration: WriteConfiguration = defaultTestWriteConfiguration): T {
    return test(bean, testName, writeConfiguration)
  }

  @Test
  fun `same list binding for the same type and annotation set`() {
    val serializer = ObjectSerializer()
    val bindingProducer = getBindingProducer(serializer)

    @Suppress("unused")
    class TestBean {
      @JvmField
      val a: MutableList<String> = SmartList()
      val b: MutableList<String> = SmartList()
    }

    serializer.write(TestBean(), ByteArrayOutputStream())
    assertThat(getBindingCount(bindingProducer)).isEqualTo(3 /* TestBean/String/Collection */)
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

    objectSerializer.writeList(listOf("foo", "bar"), String::class.java, out, WriteConfiguration(binary = false))

    val ionText = out.toString()
    out.reset()

    val result = objectSerializer.readList(String::class.java, ionText.reader())
    assertThat(result).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun `parametrized type as item`() {
    class TestBean {
      @JvmField
      val list: MutableList<Set<String>> = SmartList()
    }

    val bean = TestBean()
    bean.list.add(setOf("bar"))
    val deserializedBean = test(bean)
    assertThat(deserializedBean.list.first()).isInstanceOf(Set::class.java)
  }

  @Test
  fun `parametrized array`() {
    class TestBean<T> {
      @JvmField
      var list: Array<T>? = null
    }

    val bean = TestBean<String>()
    bean.list = arrayOf("bar")
    val deserializedBean = test(bean, defaultTestWriteConfiguration.copy(allowAnySubTypes = true))
    assertThat(deserializedBean.list!!.first()).isEqualTo("bar")
  }

  @Test
  fun `versioned file`() {
    val file = VersionedFile(fsRule.fs.getPath("/cache.ion"), 42, isCompressed = false)
    val list = listOf("foo", "bar")
    val configuration = WriteConfiguration(binary = false)
    file.writeList(list, String::class.java, configuration = configuration)
    assertThat(file.file.readChars().trim()).isEqualToIgnoringNewLines("""
      {
        version:42,
        formatVersion:2,
        data:[
          foo,
          bar
        ]
      }
    """.trimIndent())
    assertThat(file.readList(String::class.java)).isEqualTo(list)

    // test that we can read regardless of compressed setting
    VersionedFile(file.file, 42, isCompressed = true).writeList(list, String::class.java, configuration = configuration)
    assertThat(VersionedFile(file.file, 42, isCompressed = false).readList(String::class.java)).isEqualTo(list)
  }

  @Test
  fun `remove versioned file on input error`() {
    val file = VersionedFile(fsRule.fs.getPath("/cache.ion"), 42)
    file.file.write(byteArrayOf(0, 42, 0))
    assertThat(file.readList(String::class.java)).isNull()
    assertThat(file.file).doesNotExist()
  }
}