// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.psi.tree.IFileElementType
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.writeChild
import kotlin.reflect.KClass

class StubIndexBenchmark : HeavyPlatformTestCase() {

  fun testBenchmark() {
    var lastModCount = getModCount(JavaFileElementType::class)
    fun assertModCountIncreasedAtLeast(minInc: Int) {
      val modCount = getModCount(JavaFileElementType::class)
      assert(lastModCount + minInc <= modCount ||
             StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
             StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.Disabled)
      lastModCount = modCount
    }

    val srcDir = createTestProjectStructure()
    val pkg = WriteAction.compute<VirtualFile, Nothing> {
      srcDir.createChildDirectory(this, "pkg")
    }

    for (i in 0..1000) {
      pkg.writeChild("C$i.java", """
          class C$i {
            String s$i;
          }
        """.trimIndent())
    }

    srcDir.refresh(false, true)
    assertModCountIncreasedAtLeast(1)

    val durations = mutableListOf<Double>()
    repeat(25) {
      val startMs = System.currentTimeMillis()

      WriteAction.compute<Unit, Nothing> { pkg.rename(this, "pkg2") }
      srcDir.refresh(false, true)
      assertModCountIncreasedAtLeast(1)

      WriteAction.compute<Unit, Nothing> { pkg.rename(this, "pkg") }
      srcDir.refresh(false, true)
      assertModCountIncreasedAtLeast(1)

      val finishMs = System.currentTimeMillis()
      durations += (finishMs - startMs).toDouble() / 1e3
    }
    System.out.println("src = ${StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE}, mean = ${durations.sum() / durations.size} s, data = ${durations}" )
  }

  private fun <T: IFileElementType> getModCount(elementType: KClass<T>) =
    (StubIndex.getInstance() as StubIndexEx)
      .getPerFileElementTypeModificationTracker(elementType.java).modificationCount

}