// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.psi.tree.StubFileElementType
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.writeChild
import com.intellij.util.io.write
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Paths

/**
 * set "stub.index.per.file.element.type.modification.tracker.precise.check.threshold" system
 * property to ~1000 before executing and comment out @Ignore
 */
@RunWith(JUnit4::class)
class PerFileElementTypeModificationTrackerBenchmark : HeavyPlatformTestCase() {
  @Test
  @Ignore
  fun benchmark() {
    var lastModCount = getModCount(JavaParserDefinition.JAVA_FILE)
    fun assertModCountIncreasedAtLeast(minInc: Int) {
      val modCount = getModCount(JavaParserDefinition.JAVA_FILE)
      assert(lastModCount + minInc <= modCount ||
             StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
             StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.Disabled
      ) {
        "last $lastModCount + minInc $minInc <= current $modCount"
      }
      lastModCount = modCount
    }
    fun assertModCountIsSame() {
      val modCount = getModCount(JavaParserDefinition.JAVA_FILE)
      assert(lastModCount == modCount ||
             StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
             StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.Disabled
      ) {
        "last $lastModCount == current $modCount"
      }
    }

    val srcDir = createTestProjectStructure()
    val pkg = runWriteAction {
      srcDir.createChildDirectory(this, "pkg")
    }

    val filesRange = 1..500
    runWriteAction {
      for (i in filesRange) {
        pkg.writeChild("C$i.java", """
          class C$i {
            String s$i;
          }
        """.trimIndent())
      }
    }

    srcDir.refresh(false, true)
    assertModCountIncreasedAtLeast(1)

    val durations = mutableListOf<Double>()
    repeat(25) {
      val startMs = System.currentTimeMillis()
      for (i in filesRange) {
        Paths.get(pkg.path + "/C$i.java").write("""
            class C$i {
              int i$i;
            }
            """.trimIndent())
      }
      srcDir.refresh(false, true)
      assertModCountIncreasedAtLeast(1)
      ensureIndexUpToDate()

      for (i in filesRange) {
        Paths.get(pkg.path + "/C$i.java").write("""
            class C$i {
              String s$i;
            }
            """.trimIndent())
      }
      srcDir.refresh(false, true)
      assertModCountIncreasedAtLeast(1)
      ensureIndexUpToDate()

      runWriteAction { pkg.rename(this, "pkg2") }
      srcDir.refresh(false, true)
      assertModCountIsSame()
      ensureIndexUpToDate()

      runWriteAction { pkg.rename(this, "pkg") }
      srcDir.refresh(false, true)
      assertModCountIsSame()
      ensureIndexUpToDate()

      val finishMs = System.currentTimeMillis()
      durations += (finishMs - startMs).toDouble() / 1e3
    }
    System.out.println("src = ${StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE}, mean = ${"%.4f".format(durations.sum() / durations.size)} s, data = ${durations}" )
  }

  private fun ensureIndexUpToDate() {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project))
  }

  private fun getModCount(elementType: StubFileElementType<*>) =
    (StubIndex.getInstance() as StubIndexEx)
      .getPerFileElementTypeModificationTracker(elementType).modificationCount
}