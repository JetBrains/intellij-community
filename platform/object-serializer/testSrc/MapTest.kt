// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.serialization

import com.intellij.testFramework.assertions.Assertions.assertThat
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.ByteArrayOutputStream
import java.util.*

class MapTest {
  @Rule
  @JvmField
  val testName = TestName()

  private fun <T : Any> test(bean: T, writeConfiguration: WriteConfiguration = defaultTestWriteConfiguration): T {
    return test(bean, testName, writeConfiguration)
  }

  @Test
  fun `same map binding for the same type and annotation set`() {
    val serializer = ObjectSerializer()
    val bindingProducer = getBindingProducer(serializer)

    @Suppress("unused")
    class TestBean {
      @JvmField
      val a: MutableMap<String, String> = HashMap()
      val b: MutableMap<String, String> = HashMap()
    }

    serializer.write(TestBean(), ByteArrayOutputStream())
    assertThat(getBindingCount(bindingProducer)).isEqualTo(3 /* TestBean/String/Map */)
  }

  @Test
  fun map() {
    val bean = TestMapBean()
    bean.map.put("foo", "bar")
    test(bean)
  }

  @Test
  fun `enum map`() {
    class TestBean {
      @JvmField
      val map = EnumMap<TestEnum, String>(TestEnum::class.java)
    }

    val bean = TestBean()
    bean.map.put(TestEnum.BLUE, "red")
    bean.map.put(TestEnum.RED, "blue")
    val deserializedBean = test(bean)
    assertThat(deserializedBean.map).isInstanceOf(EnumMap::class.java)
  }

  @Test
  fun `parametrized type as map value`() {
    class TestBean {
      @JvmField
      val map: MutableMap<String, Set<String>> = HashMap()
    }

    val bean = TestBean()
    bean.map.put("bar", setOf("b"))
    val deserializedBean = test(bean)
    assertThat(deserializedBean.map.values.first()).isInstanceOf(Set::class.java)
  }

  @Test
  fun `empty map`() {
    class TestBean {
      @JvmField
      val map: MutableMap<String, Set<String>> = HashMap()
    }

    val bean = TestBean()
    test(bean, defaultTestWriteConfiguration.copy(filter = SkipNullAndEmptySerializationFilter))
  }

  @Test
  fun `bean map`() {
    val bean = TestMapBean()

    val key = TestMapBean()
    key.map.put("foo", "bar")
    key.map.put("A", "VB")
    key.map.put("Z", "123")
    key.map.put("a-A", "123")

    val value = TestMapBean()
    value.map.put("some key", "some value")

    bean.beanMap.put(key, value)
    test(bean, WriteConfiguration(binary = false, orderMapEntriesByKeys = true))
  }

  @Test
  fun `fastutil Int2Int`() {
    class Bean {
      @JvmField val map = Int2IntOpenHashMap()
    }

    val bean = Bean()
    bean.map.put(42, 12)
    bean.map.put(43, 15)

    test(bean, WriteConfiguration(binary = false, orderMapEntriesByKeys = true))
  }
}

private class TestMapBean {
  @JvmField
  val map: MutableMap<String, String> = HashMap()

  @JvmField
  val beanMap: MutableMap<TestMapBean, TestMapBean> = HashMap()

  @JvmField
  val emptyMap = emptyMap<String, String>()
}
