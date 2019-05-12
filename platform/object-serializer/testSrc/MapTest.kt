// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import gnu.trove.THashMap
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class MapTest {
  @Rule
  @JvmField
  val testName = TestName()

  private fun test(bean: Any, writeConfiguration: WriteConfiguration? = null) = test(bean, testName, writeConfiguration)

  @Test
  fun map() {
    val bean = TestMapBean()
    bean.map.put("foo", "bar")
    test(bean)
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
    test(bean, WriteConfiguration(binary = false, filter = FILTER, orderMapEntriesByKeys = true))
  }
}

private class TestMapBean {
  @JvmField
  val map: MutableMap<String, String> = THashMap()

  @JvmField
  val beanMap: MutableMap<TestMapBean, TestMapBean> = THashMap()
}
