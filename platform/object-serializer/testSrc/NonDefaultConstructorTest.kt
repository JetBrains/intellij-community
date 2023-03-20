// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.Assertions.assertThatThrownBy
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class NonDefaultConstructorTest {
  @Rule
  @JvmField
  val testName = TestName()

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  private fun test(bean: Any, writeConfiguration: WriteConfiguration = defaultTestWriteConfiguration) = test(bean, testName, writeConfiguration)

  @Test
  fun `no default constructor`() {
    test(NoDefaultConstructorBean("foo", arrayListOf(42, 21)))
  }

  @Test
  fun `property mapping provider`() {
    @Suppress("unused")
    class NoDefaultConstructorAndNoAnnotationBean(@JvmField val someParameter: String, @JvmField val intList: List<Int>)

    test(NoDefaultConstructorAndNoAnnotationBean("foo", arrayListOf(42, 21)), testName, defaultTestWriteConfiguration, ReadConfiguration(resolvePropertyMapping = {
      val clazz = NoDefaultConstructorAndNoAnnotationBean::class.java
      if (it.name == clazz.name) {
        NonDefaultConstructorInfo(listOf("someParameter", "intList"), clazz.constructors.first())
      }
      else {
        null
      }
    }))
  }

  @Test
  fun `kotlin data class`() {
    data class NoDefaultConstructorAndNoAnnotationBean(@JvmField val someParameter: String, @JvmField val intList: List<Int>)

    test(NoDefaultConstructorAndNoAnnotationBean("foo", arrayListOf(42, 21)))
  }

  @Test
  fun `no annotation and class written in a poor language`() {
    // test that Java classes are not affected by Kotlin classes support
    assertThatThrownBy {
      test(ClassInPoorLanguage("foo"))
    }.hasMessageStartingWith("Please annotate non-default constructor with PropertyMapping")
  }

  @Test
  fun `skipped empty list and not null parameter`() {
    // must be some list impl and not just emptyList(), because otherwise test will be not representative (empty list is handled in another way
    test(NoDefaultConstructorBean("foo", ArrayList()), defaultTestWriteConfiguration.copy(filter = SkipNullAndEmptySerializationFilter))
  }

  @Test
  fun `null string as arg`() {
    test(NullableArgBean("foo", null, ""))
  }

  @Test
  fun `id registration`() {
    // test that bean created by non-default constructor is registered in object table
    val bean = ContainingBean()
    bean.b = Bean2("foo")
    bean.b2 = Bean3()
    bean.b2!!.b = bean.b
    test(bean)
  }

  @Test
  fun `remove versioned file on parameter error`() {
    val disposable = Disposer.newDisposable()
    com.intellij.openapi.diagnostic.DefaultLogger.disableStderrDumping(disposable)
    try {

      val file = VersionedFile(fsRule.fs.getPath("/cache.ion"), 42, isCompressed = false)
      file.file.write("""
      {
        version:42,
        formatVersion:3,
        data:{
        }
      }
    """.trimIndent())
      assertThatThrownBy {
        file.read(NoDefaultConstructorBean::class.java)
      }
        .isInstanceOf(AssertionError::class.java)
        .hasCauseInstanceOf(SerializationException::class.java)
      assertThat(file.file).doesNotExist()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `nested list`() {
    val ionText = """
      {
        '@id':0,
        gradleHomeDir:'/Volumes/data/.gradle/wrapper/dists/gradle-5.4.1-bin/e75iq110yv9r9wt1a6619x2xm/gradle-5.4.1',
        classpathEntries:[
          {
            '@id':1,
            classesFile:[
              "/Volumes/data/.gradle/caches/modules-2/files-2.1/com.gradle/build-scan-plugin/2.1/bade2a9009f96169d2b25d3f2023afb2cdf8119f/build-scan-plugin-2.1.jar"
            ],
            sourcesFile:0,
            javadocFile:0
          }
        ],
        owner:{
          '@id':93,
          id:GRADLE,
          readableName:Gradle
        }
      }
    """.trimIndent()

    objectSerializer.read(BuildScriptClasspathData::class.java, ionText)
  }
}

@Suppress("unused")
private class ProjectSystemId @PropertyMapping("id", "readableName") constructor(@JvmField val id: String, @JvmField val readableName: String)

@Suppress("unused")
private class ClasspathEntry @PropertyMapping("classesFile", "sourcesFile", "javadocFile") constructor(@JvmField val classesFile: MutableSet<String>,
                                                                                                       @JvmField val sourcesFile: MutableSet<String>,
                                                                                                       @JvmField val javadocFile: MutableSet<String>)

@Suppress("unused")
private class BuildScriptClasspathData @PropertyMapping("owner", "classpathEntries") constructor(@JvmField val owner: ProjectSystemId, @JvmField val classpathEntries: MutableList<ClasspathEntry>)

private class ContainingBean {
  @JvmField
  var b: Bean2? = null
  var b2: Bean3? = null
}

private class Bean2 @PropertyMapping("name") constructor(@JvmField val name: String)

private class Bean3 {
  @JvmField
  var b: Bean2? = null
}

@Suppress("unused")
private class NoDefaultConstructorBean @PropertyMapping("someParameter", "intList") constructor(@JvmField val someParameter: String,
                                                                                                  @JvmField val intList: List<Int>)

@Suppress("unused")
private class NullableArgBean @PropertyMapping("p", "p2", "p3") constructor(@JvmField val p: String?, @JvmField val p2: String?, @JvmField val p3: String?)
