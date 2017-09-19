/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author nik
 */
class ClassFileBuilderTest {
  @Test
  fun `empty class`() {
    val dir = directoryContent {
      classFile("A") {}
    }.generateInTempDir()
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
    }.generateInTempDir()
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
    }.generateInTempDir()
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
    }.generateInTempDir()
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
