// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.networknt.schema.path.NodePath
import com.networknt.schema.path.PathType
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language

class PsiLocationIndexTest : BasePlatformTestCase() {

  private fun buildIndex(@Language("JSON") json: String): PsiLocationIndex {
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, json) as JsonFile
    val rootElement = psiFile.topLevelValue!!
    val walker = JsonLikePsiWalker.getWalker(rootElement)!!
    return PsiLocationIndex.build(walker, rootElement)
  }

  fun `test empty object builds index with root`() {
    val index = buildIndex("{}")
    assertThat(index.resolve(NodePath(PathType.JSON_POINTER))).isNotNull
  }

  fun `test flat properties are indexed`() {
    val index = buildIndex("""{"name": "Alice", "age": 30}""")
    val root = NodePath(PathType.JSON_POINTER)
    assertThat(index.resolve(root)).isNotNull

    val namePath = root.append("name")
    assertThat(index.resolve(namePath)).isNotNull
    assertThat(index.resolve(namePath)!!.text).isEqualTo("\"Alice\"")

    val agePath = root.append("age")
    assertThat(index.resolve(agePath)).isNotNull
    assertThat(index.resolve(agePath)!!.text).isEqualTo("30")
  }

  fun `test nested objects are indexed`() {
    val index = buildIndex("""{"address": {"city": "NYC", "zip": "10001"}}""")
    val root = NodePath(PathType.JSON_POINTER)

    val addressPath = root.append("address")
    assertThat(index.resolve(addressPath)).isNotNull

    val cityPath = addressPath.append("city")
    assertThat(index.resolve(cityPath)).isNotNull
    assertThat(index.resolve(cityPath)!!.text).isEqualTo("\"NYC\"")
  }

  fun `test array elements are indexed`() {
    val index = buildIndex("""{"items": [10, 20, 30]}""")
    val root = NodePath(PathType.JSON_POINTER)

    val itemsPath = root.append("items")
    assertThat(index.resolve(itemsPath)).isNotNull

    val item0 = itemsPath.append(0)
    assertThat(index.resolve(item0)).isNotNull
    assertThat(index.resolve(item0)!!.text).isEqualTo("10")

    val item2 = itemsPath.append(2)
    assertThat(index.resolve(item2)).isNotNull
    assertThat(index.resolve(item2)!!.text).isEqualTo("30")
  }

  fun `test nested array of objects`() {
    val index = buildIndex("""{"items": [{"name": "a"}, {"name": "b"}]}""")
    val root = NodePath(PathType.JSON_POINTER)

    val item0Name = root.append("items").append(0).append("name")
    assertThat(index.resolve(item0Name)).isNotNull
    assertThat(index.resolve(item0Name)!!.text).isEqualTo("\"a\"")

    val item1Name = root.append("items").append(1).append("name")
    assertThat(index.resolve(item1Name)).isNotNull
    assertThat(index.resolve(item1Name)!!.text).isEqualTo("\"b\"")
  }

  fun `test resolvePropertyName returns key element`() {
    val index = buildIndex("""{"name": "Alice"}""")
    val root = NodePath(PathType.JSON_POINTER)

    val nameElement = index.resolvePropertyName(root, "name")
    assertThat(nameElement).isNotNull
    assertThat(nameElement!!.text).isEqualTo("\"name\"")
  }

  fun `test resolvePropertyValue returns value element`() {
    val index = buildIndex("""{"name": "Alice"}""")
    val root = NodePath(PathType.JSON_POINTER)

    val valueElement = index.resolvePropertyValue(root, "name")
    assertThat(valueElement).isNotNull
    assertThat(valueElement!!.text).isEqualTo("\"Alice\"")
  }

  fun `test missing path returns null`() {
    val index = buildIndex("""{"name": "Alice"}""")
    val root = NodePath(PathType.JSON_POINTER)

    assertThat(index.resolve(root.append("nonexistent"))).isNull()
    assertThat(index.resolvePropertyName(root, "nonexistent")).isNull()
    assertThat(index.resolvePropertyValue(root, "nonexistent")).isNull()
  }

  fun `test deeply nested path`() {
    val index = buildIndex("""{"a": {"b": {"c": {"d": "deep"}}}}""")
    val root = NodePath(PathType.JSON_POINTER)

    val deepPath = root.append("a").append("b").append("c").append("d")
    assertThat(index.resolve(deepPath)).isNotNull
    assertThat(index.resolve(deepPath)!!.text).isEqualTo("\"deep\"")
  }

  fun `test mixed arrays and objects`() {
    val index = buildIndex("""
      {
        "users": [
          {"name": "Alice", "tags": ["admin", "user"]},
          {"name": "Bob", "tags": ["user"]}
        ]
      }
    """.trimIndent())
    val root = NodePath(PathType.JSON_POINTER)

    // users[0].tags[1]
    val tag = root.append("users").append(0).append("tags").append(1)
    assertThat(index.resolve(tag)).isNotNull
    assertThat(index.resolve(tag)!!.text).isEqualTo("\"user\"")

    // users[1].name
    val bobName = root.append("users").append(1).append("name")
    assertThat(index.resolve(bobName)).isNotNull
    assertThat(index.resolve(bobName)!!.text).isEqualTo("\"Bob\"")
  }
}
