// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.graphql

import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.inputStreamIfExists
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class CachingGraphQLQueryLoaderTest {

  @Rule
  @JvmField
  val testDataRule = TemporaryFolder()

  private lateinit var loader: CachingGraphQLQueryLoader
  private lateinit var queryFile: File
  private lateinit var fragmentFolder: File

  @Before
  fun setUp() {
    loader = CachingGraphQLQueryLoader(getFileStream = {
      testDataRule.root.toPath().resolve(it).inputStreamIfExists()
    })
    testDataRule.newFolder("graphql", "query")
    fragmentFolder = testDataRule.newFolder("graphql", "fragment")
    queryFile = testDataRule.newFile("graphql/query/q.graphql")
  }


  @Test
  fun testSimpleQuery() {
    val source = """
      query(id: ID!) {
          node(id: id) {
              name
          }
      }
    """.trimIndent()
    val loaded = writeAndLoadQuery(source)
    check(loaded, source)
  }

  @Test
  fun testQueryWithInnerFragment() {
    val source = """
      fragment inner on Something {
          name
      }
      
      query(id: ID!) {
          node(id: id) {
              name
              ...inner
          }
      }
    """.trimIndent()
    val loaded = writeAndLoadQuery(source)
    check(loaded, source)
  }

  @Test
  fun testQueryWithOutsideFragment() {
    val fragmentSource = """      
      fragment outer on Something {
          name
      }
    """.trimIndent()
    writeFragment("outer", fragmentSource)

    val querySource = """      
      query(id: ID!) {
          node(id: id) {
              name
              ...outer
          }
      }
    """.trimIndent()

    val loaded = writeAndLoadQuery(querySource)
    check(loaded, fragmentSource, querySource)
  }

  @Test
  fun testQueryWithInnerAndOutsideFragment() {
    val fragmentSource = """      
      fragment outer on Something {
          name
      }
    """.trimIndent()
    writeFragment("outer", fragmentSource)

    val querySource = """
      fragment inner on Something {
          ...outer
          name
      }
            
      query(id: ID!) {
          node(id: id) {
              name
              ...inner
              ...outer
          }
      }
    """.trimIndent()

    val loaded = writeAndLoadQuery(querySource)
    check(loaded, fragmentSource, querySource)
  }

  @Test
  fun testCircularFragments() {
    val fragment1Source = """      
      fragment outer1 on Something {
          ...outer2
      }
    """.trimIndent()
    writeFragment("outer1", fragment1Source)

    val fragment2Source = """      
      fragment outer2 on Something {
          ...outer1
      }
    """.trimIndent()
    writeFragment("outer2", fragment2Source)

    val querySource = """
      fragment inner on Something {
          name
      }
            
      query(id: ID!) {
          node(id: id) {
              name
              ...outer1
              ...outer2
          }
      }
    """.trimIndent()

    val loaded = writeAndLoadQuery(querySource)
    check(loaded, fragment2Source, fragment1Source, querySource)
  }

  @Test
  fun testDependencyOrder() {
    val fragment1Source = """      
      fragment outer1 on Something {
          ...outer2
      }
    """.trimIndent()
    writeFragment("outer1", fragment1Source)

    val fragment2Source = """      
      fragment outer2 on Something {
          name
      }
    """.trimIndent()
    writeFragment("outer2", fragment2Source)

    val querySource = """
      fragment inner on Something {
          name
      }
            
      query(id: ID!) {
          node(id: id) {
              name
              ...outer1
          }
      }
    """.trimIndent()

    val loaded = writeAndLoadQuery(querySource)
    check(loaded, fragment2Source, fragment1Source, querySource)
  }

  private fun writeFragment(name: String, source: String) {
    val file = fragmentFolder.toPath().resolve("$name.graphql").createParentDirectories().createFile()
    file.writeText(source)
  }

  private fun writeAndLoadQuery(querySource: String): String {
    queryFile.writeText(querySource)
    return loader.loadQuery(queryFile.path)
  }

  private fun check(loaded: String, vararg sources: String) {
    val trimmedSource = sources.joinToString("\n").lines().map(String::trim).joinToString("\n") { it }
    assertEquals(trimmedSource, loaded)
  }
}
