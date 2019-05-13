// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.SmartList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class ListTest {
  @Rule
  @JvmField
  val testName = TestName()

  private fun <T : Any> test(bean: T, writeConfiguration: WriteConfiguration? = null): T {
    return test(bean, testName, writeConfiguration)
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
}