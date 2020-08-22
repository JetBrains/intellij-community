// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.java

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.util.io.directoryContent
import junit.framework.TestCase.assertSame
import org.junit.Test
import java.io.File
import java.io.Serializable
import java.lang.reflect.Modifier
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassFileBuilderTest {
  @Test
  fun `empty class`() {
    val dir = directoryContent {
      classFile("A") {}
    }.generateInTempDir().toFile()
    val aClass = loadClass("A", File(dir, "A.class"))
    assertSame(Object::class.java, aClass.superclass)
    assertEmpty(aClass.interfaces)
    assertEmpty(aClass.declaredFields)
  }

  @Test
  fun `class with extends and implements`() {
    val dir = directoryContent {
      classFile("A") {
        superclass = ArrayList::class.java.name
        interfaces = listOf(Serializable::class.java.name)
      }
    }.generateInTempDir().toFile()
    val aClass = loadClass("A", File(dir, "A.class"))
    assertSame(ArrayList::class.java, aClass.superclass)
    assertSame(Serializable::class.java, assertOneElement(aClass.interfaces))
    assertEmpty(aClass.declaredFields)
  }

  @Test
  fun `class with fields`() {
    val dir = directoryContent {
      classFile("A") {
        field("foo", Int::class, AccessModifier.PUBLIC)
        field("bar", Object::class.java.name)
      }
    }.generateInTempDir().toFile()
    val aClass = loadClass("A", File(dir, "A.class"))
    val foo = aClass.getDeclaredField("foo")
    assertSame(Int::class.java, foo.type)
    assertTrue(Modifier.isPublic(foo.modifiers))
    val bar = aClass.getDeclaredField("bar")
    assertSame(Object::class.java, bar.type)
    assertTrue(Modifier.isPrivate(bar.modifiers))
  }

  @Test
  fun `class in non-default package`() {
    val dir = directoryContent {
      classFile("p.A") {}
    }.generateInTempDir().toFile()
    val aClass = loadClass("p.A", File(dir, "p/A.class"))
    assertEquals("p.A", aClass.name)
  }
}

private fun loadClass(name: String, file: File): Class<*> {
  return MyClassLoader(ClassFileBuilderTest::class.java.classLoader).doDefineClass(name, file.readBytes())
}

private class MyClassLoader(parent: ClassLoader) : ClassLoader(parent) {
  fun doDefineClass(name: String, data: ByteArray): Class<*> {
    return defineClass(name, data, 0, data.size)
  }
}
