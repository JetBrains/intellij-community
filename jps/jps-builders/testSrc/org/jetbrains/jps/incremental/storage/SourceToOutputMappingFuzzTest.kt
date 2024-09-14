// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import net.jqwik.api.*
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class SourceToOutputMappingFuzzTest {
  companion object {
    init {
      System.setProperty("jps.source.to.output.mapping.check.collisions", "true")
    }
  }

  private lateinit var mapping: ExperimentalSourceToOutputMapping
  private var file: Path? = null

  @BeforeProperty
  fun setUp() {
    file = Files.createTempFile("mvstore", ".db")
    val storageManager = StorageManager(file!!, 0)
    mapping = ExperimentalSourceToOutputMapping.createSourceToOutputMap(
      storageManager = storageManager,
      relativizer = PathRelativizerService(),
      targetId = "test-module",
      targetTypeId = "java"
    )
  }

  @AfterProperty
  fun tearDown() {
    try {
      mapping.close()
    }
    finally {
      file?.let { Files.deleteIfExists(it) }
    }
  }

  @Provide
  fun pathStrings(): Arbitrary<String> {
    return Arbitraries.strings().alpha().numeric().withChars('/').ofMinLength(2).ofMaxLength(255)
  }

  @Suppress("unused")
  @Provide
  fun pathStringLists(): Arbitrary<List<String>> {
    return pathStrings().list().ofMinSize(1).ofMaxSize(32)
  }

  @Suppress("unused")
  @Provide
  fun pathStringListsList(): Arbitrary<List<List<String>>> {
    return pathStrings().list().ofMinSize(1).ofMaxSize(32).list().ofMinSize(1).ofMaxSize(64)
  }

  @Property
  fun setOutputs(@ForAll("pathStrings") source: String, @ForAll("pathStringLists") outputs: List<String>) {
    mapping.setOutputs(source, outputs)
    val result = mapping.getOutputs(source)
    assertThat(result).isNotNull()
    assertThat(result).containsExactlyInAnyOrderElementsOf(outputs)

    checkCursorAndSourceIterator(source, outputs)
  }

  @Property
  fun appendOutput(@ForAll("pathStrings") source: String, @ForAll("pathStrings") output: String) {
    mapping.appendOutput(source, output)
    val outputs = mapping.getOutputs(source)
    assertThat(outputs).isNotNull
    assertThat(outputs).contains(output)

    checkCursorAndSourceIterator(source, outputs!!)
  }

  @Property
  fun cursor(@ForAll("pathStringLists") sources: List<String>, @ForAll("pathStringListsList") outputs: List<List<String>>) {
    mapping.clean()

    val expectedMap = HashMap<String, List<String>>()
    for (source in sources) {
      val list = outputs[Random.nextInt(outputs.size)]
      expectedMap.put(source, list)
      mapping.setOutputs(source, list)
    }

    val actualMap = LinkedHashMap<String, List<String>>()
    val cursor = mapping.cursor()
    while (cursor.hasNext()) {
      actualMap.put(cursor.next(), cursor.outputPaths.asList())
    }

    assertThat(actualMap).isEqualTo(expectedMap)
  }

  private fun checkCursorAndSourceIterator(source: String, outputs: List<String>) {
    val cursor = mapping.cursor()
    val map = LinkedHashMap<String, List<String>>()
    while (cursor.hasNext()) {
      val next = cursor.next()
      if (next == source) {
        map.put(source, cursor.outputPaths.asList())
      }
    }
    assertThat(map).isEqualTo(mapOf(source to outputs))

    assertThat(mapping.sourcesIterator.asSequence().contains(source)).isTrue()
  }
}