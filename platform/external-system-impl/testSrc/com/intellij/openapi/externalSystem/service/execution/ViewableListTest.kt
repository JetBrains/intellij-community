// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.externalSystem.util.ViewableList
import com.intellij.testFramework.UsefulTestCase

class ViewableListTest : UsefulTestCase() {

  fun `test simple modification`() {
    val regularList = ArrayList<Int>()
    val source = ArrayList<Int>()
    val viewableList = object : ViewableList<Int>() {
      override val size: Int
        get() = source.size

      override fun add(index: Int, element: Int) {
        source.add(index, element)
      }

      override fun set(index: Int, element: Int): Int {
        return source.set(index, element)
      }

      override fun removeAt(index: Int): Int {
        return source.removeAt(index)
      }

      override fun get(index: Int): Int {
        return source[index]
      }
    }

    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.add(11), viewableList.add(11))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.add(12), viewableList.add(12))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.add(13), viewableList.add(13))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.remove(12), viewableList.remove(12))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.add(11), viewableList.add(11))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.remove(11), viewableList.remove(11))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.remove(11), viewableList.remove(11))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.remove(11), viewableList.remove(11))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
    assertEquals(regularList.remove(13), viewableList.remove(13))
    assertEquals(regularList, viewableList)
    assertEquals(regularList.size, viewableList.size)
  }
}