// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.plugins.loadExtensionWithText
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.lastModified
import org.junit.Assert
import java.nio.file.Files
import kotlin.streams.toList

class IndexInfrastructureExtensionTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test infrastructure extension drops all indexes when it requires invalidation`() {
    val text = "<fileBasedIndexInfrastructureExtension implementation=\"" + TestIndexInfrastructureExtension::class.java.name + "\"/>"
    Disposer.register(testRootDisposable, loadExtensionWithText(text))

    val before = Files.list(PathManager.getIndexRoot()).use {
      it.toList().associate { p -> p.fileName.toString() to p.lastModified().toMillis() }.toSortedMap()
    }

    val switcher = FileBasedIndexTumbler("test")
    switcher.turnOff()
    switcher.turnOn()

    val after = Files.list(PathManager.getIndexRoot()).use {
      it.toList().associate { p -> p.fileName.toString() to p.lastModified().toMillis() }.toSortedMap()
    }

    var containsExtensionCaches = false
    for ((b, a) in (before.entries zip after.entries)) {
      Assert.assertEquals(b.key, a.key)
      Assert.assertTrue(a.value > b.value)
      if (a.key.endsWith(testInfraExtensionFile)) {
        containsExtensionCaches = true
      }
    }
    Assert.assertTrue(containsExtensionCaches)
  }
}

const val testInfraExtensionFile = "_test_extension"

@InternalIgnoreDependencyViolation
class TestIndexInfrastructureExtension : FileBasedIndexInfrastructureExtension {
  override fun createFileIndexingStatusProcessor(project: Project): FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor? =
    null

  override fun <K : Any?, V : Any?> combineIndex(indexExtension: FileBasedIndexExtension<K, V>,
                                                 baseIndex: UpdatableIndex<K, V, FileContent, *>): UpdatableIndex<K, V, FileContent, *>? {
    Files.createFile(PathManager.getIndexRoot().resolve(indexExtension.name.name + testInfraExtensionFile))
    return null
  }

  override fun onFileBasedIndexVersionChanged(indexId: ID<*, *>) = Unit

  override fun onStubIndexVersionChanged(indexId: StubIndexKey<*, *>) = Unit

  override fun initialize(indexLayoutId: String?): FileBasedIndexInfrastructureExtension.InitializationResult
  = FileBasedIndexInfrastructureExtension.InitializationResult.INDEX_REBUILD_REQUIRED

  override fun resetPersistentState() = Unit

  override fun resetPersistentState(indexId: ID<*, *>) = Unit

  override fun shutdown() = Unit

  override fun getVersion(): Int = 0

}