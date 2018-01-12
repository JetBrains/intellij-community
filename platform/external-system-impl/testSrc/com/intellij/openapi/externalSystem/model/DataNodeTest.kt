/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.model


import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import java.io.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URL
import java.net.URLClassLoader


class DataNodeTest {

  lateinit var cl: ClassLoader
  private val myLibUrl: URL = javaClass.classLoader.getResource("dataNodeTest/lib.jar")

  @Before
  fun setUp() {
    cl = URLClassLoader(arrayOf(myLibUrl), javaClass.classLoader)
  }

  @Test
  fun `instance of class from a classloader can be deserialized`() {
    val barObject = cl.loadClass("foo.Bar").newInstance()

    val deserialized = wrapAndDeserialize(Any::class.java, barObject)

    assertThatExceptionOfType(IllegalStateException::class.java)
      .isThrownBy { deserialized.prepareData(javaClass.classLoader) }

    val newCl = URLClassLoader(arrayOf(myLibUrl), javaClass.classLoader)

    deserialized.prepareData(newCl)
    assertThat(deserialized.data.javaClass.name)
      .contains("foo.Bar")
  }

  @Test
  fun `proxy instance can be deserialized`() {
    val interfaceClass = cl.loadClass("foo.Baz")

    val invocationHandler = object : InvocationHandler, Serializable {
      override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?) = 0
    }

    var proxyInstance = Proxy.newProxyInstance(cl, arrayOf(interfaceClass), invocationHandler)
    val deserialized = wrapAndDeserialize(interfaceClass as Class<Any>, proxyInstance)


    assertThatExceptionOfType(IllegalStateException::class.java)
      .isThrownBy { deserialized.prepareData(javaClass.classLoader) }

    val newCl = URLClassLoader(arrayOf(myLibUrl), javaClass.classLoader)

    deserialized.prepareData(newCl)
    assertThat(deserialized.data.javaClass.interfaces)
      .extracting("name")
      .contains("foo.Baz")
  }


  private fun wrapAndDeserialize(clz: Class<Any>,
                                 barObject: Any): DataNode<Any> {
    val original = DataNode(Key.create(clz, 0), barObject, null)
    val bos = ByteArrayOutputStream()
    ObjectOutputStream(bos).use { it.writeObject(original) }
    val bytes = bos.toByteArray()
    return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as DataNode<Any>
  }
}


