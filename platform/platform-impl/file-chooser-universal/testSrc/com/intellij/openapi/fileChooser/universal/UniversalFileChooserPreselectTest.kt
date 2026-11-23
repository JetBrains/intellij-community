// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * A regression test for IJPL-253879.
 *
 * The first preselect request resolves to the last opened path. A gate delays this request.
 * The explicit request then completes first. The delayed request must not replace the explicit path.
 */
@TestApplication
class UniversalFileChooserPreselectTest {
  private val projectFixture = projectFixture()

  @TempDir
  lateinit var root: Path

  private fun contributor(): UniversalFileChooserContributor = object : UniversalFileChooserContributor {
    override val tabTitle: String = "Test"
    override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> =
      listOf(UniversalFileChooserContributor.asDefaultRoot(root))

    override fun ownsPath(path: Path): Boolean = path.startsWith(root)
  }

  @Test
  fun `a delayed request for the last opened path does not replace the explicit path`() {
    val project = projectFixture.get()
    val last = Files.createDirectories(root.resolve("last"))
    val explicit = Files.createDirectories(root.resolve("explicit"))
    FileChooserUtil.setLastOpenedFile(project, last)

    val disposable = Disposer.newDisposable()
    try {
      runInEdtAndWait {
        val descriptor = FileChooserDescriptor(true, true, false, false)
        val panel = UniversalFileChooser.Panel(disposable, descriptor, project, okAction = {}, contributors = listOf(contributor()))
        val gate = CompletableDeferred<Unit>()
        val delayedRequest = panel.preselect {
          gate.await()
          null
        }
        panel.preselect { explicit }

        PlatformTestUtil.waitWithEventsDispatching({ "The explicit path is not selected: ${panel.getSelectedFiles()}" },
                                                   { panel.getSelectedFiles() == listOf(explicit) }, 10)

        gate.complete(Unit)
        PlatformTestUtil.waitWithEventsDispatching("The delayed request does not complete", { delayedRequest.isCompleted }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertThat(panel.getSelectedFiles()).containsExactly(explicit)
      }
    }
    finally {
      runInEdtAndWait { Disposer.dispose(disposable) }
    }
  }
}
