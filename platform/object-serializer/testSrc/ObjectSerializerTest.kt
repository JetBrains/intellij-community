// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.assertions.Assertions.assertThat
import gnu.trove.THashMap
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.nio.file.Paths
import java.util.*

class ObjectSerializerTest {
  @Rule
  @JvmField
  val testName = TestName()

  private fun <T : Any> test(bean: T, writeConfiguration: WriteConfiguration = defaultTestWriteConfiguration): T {
    return test(bean, testName, writeConfiguration)
  }

  @Test
  fun threadLocalPooledBlockAllocatorProvider() {
    testThreadLocalPooledBlockAllocatorProvider()
  }

  @Test
  fun `same bean binding regardless of type parameters`() {
    val serializer = ObjectSerializer()
    getBinding(TestGenericBean::class.java, serializer)
    assertThat(getBindingCount(getBindingProducer(serializer))).isEqualTo(1)
  }

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

    // test SkipNullAndEmptySerializationFilter
    test(bean, defaultTestWriteConfiguration.copy(filter = SkipNullAndEmptySerializationFilter))
  }

  @Test
  fun `array of string`() {
    test(TestArrayBean(list = arrayOf("foo", "bar")))
  }

  @Test
  fun `array of objects`() {
    test(TestArrayBean(children = arrayOf(TestBean(foo = "foo"), TestBean(foo = "or bar"))))
  }

  @Test
  fun `byte array`() {
    test(TestByteArray(data = Base64.getEncoder().encode("some data".toByteArray())))
  }

  @Test
  fun enum() {
    val bean = TestEnumBean()
    bean.color = TestEnum.RED
    test(bean)
  }

  @Test
  fun `file and path`() {
    assumeTrue(!SystemInfo.isWindows)

    @Suppress("unused")
    class TestBean {
      @JvmField
      var f = File("/foo")

      @JvmField
      var p = Paths.get("/bar")
    }

    test(TestBean())
  }

  @Test
  fun `interface type for field`() {
    class TestInterfaceBean {
      @JvmField
      @field:Property(allowedTypes = [Circle::class, Rectangle::class])
      var shape: Shape? = null
    }

    val bean = TestInterfaceBean()
    bean.shape = Circle()
    test(bean)
  }

  @Test
  fun `interface type for map value - allowSubTypes`() {
    class TestInterfaceBean {
      @JvmField
      val shape: MutableMap<String, Shape> = THashMap()
    }

    val bean = TestInterfaceBean()
    bean.shape.put("first", Circle())
    test(bean, defaultTestWriteConfiguration.copy(allowAnySubTypes = true))
  }

  @Test
  fun `interface type for field - allowSubTypes`() {
    class TestInterfaceBean {
      @JvmField
      var shape: Shape? = null
    }

    val bean = TestInterfaceBean()
    bean.shape = Circle()
    test(bean, defaultTestWriteConfiguration.copy(allowAnySubTypes = true))
  }
}

private interface Shape

private class Circle : Shape {
  @JvmField
  var name: String? = null
}

private class Rectangle : Shape {
  @JvmField
  var length: Int = -1
}


internal enum class TestEnum {
  RED, BLUE
}

private class TestEnumBean {
  @JvmField
  var color: TestEnum = TestEnum.BLUE
}

private class TestByteArray @JvmOverloads constructor(@Suppress("unused") @JvmField var data: ByteArray? = null)

private class TestArrayBean(
  @JvmField var list: Array<String>? = null,
  /*  test set to final field */@JvmField val children: Array<TestBean> = arrayOf()
)

internal class TestObjectBean {
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

private class TestGenericBean<T> {
  @JvmField
  var data: TestGenericBean<T>? = null
}