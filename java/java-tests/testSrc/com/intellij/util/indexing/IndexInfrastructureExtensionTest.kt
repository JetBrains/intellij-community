// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert
import java.nio.file.Files
import kotlin.io.path.getLastModifiedTime

class IndexInfrastructureExtensionTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test infrastructure extension drops all indexes when it requires invalidation`() {
    val text = "<fileBasedIndexInfrastructureExtension implementation=\"" + TestIndexInfrastructureExtension::class.java.name + "\"/>"
    Disposer.register(testRootDisposable, loadExtensionWithText(text))

    val before = Files.list(PathManager.getIndexRoot()).use {
      it.toList().associate { p -> p.fileName.toString() to p.getLastModifiedTime().toMillis() }.toSortedMap()
    }

    val switcher = FileBasedIndexTumbler("IndexInfrastructureExtensionTest")
    switcher.turnOff()
    switcher.turnOn()

    val after = Files.list(PathManager.getIndexRoot()).use {
      it.toList().associate { p -> p.fileName.toString() to p.getLastModifiedTime().toMillis() }.toSortedMap()
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

const val testInfraExtensionFile: String = "_test_extension"

@InternalIgnoreDependencyViolation
class TestIndexInfrastructureExtension : FileBasedIndexInfrastructureExtension {
  override fun createFileIndexingStatusProcessor(project: Project): FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor? =
    null

  override fun <K : Any?, V : Any?> combineIndex(indexExtension: FileBasedIndexExtension<K, V>,
                                                 baseIndex: UpdatableIndex<K, V, FileContent, *>): UpdatableIndex<K, V, FileContent, *>? {
    Files.createFile(PathManager.getIndexRoot().resolve(indexExtension.name.name + testInfraExtensionFile))
    return null
  }

  override fun onFileBasedIndexVersionChanged(indexId: ID<*, *>) {}

  override fun onStubIndexVersionChanged(indexId: StubIndexKey<*, *>) {}

  override fun initialize(indexLayoutId: String?): FileBasedIndexInfrastructureExtension.InitializationResult
  = FileBasedIndexInfrastructureExtension.InitializationResult.INDEX_REBUILD_REQUIRED

  override fun attachData(project: Project) {}

  override fun resetPersistentState() {}

  override fun resetPersistentState(indexId: ID<*, *>) {}

  override fun shutdown() {}

  override fun getVersion(): Int = 0

}
