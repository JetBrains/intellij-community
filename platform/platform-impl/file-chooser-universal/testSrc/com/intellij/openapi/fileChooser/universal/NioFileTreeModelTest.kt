// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
class NioFileTreeModelTest {
  @TempDir
  lateinit var root: Path

  private fun descriptor(): FileChooserDescriptor = FileChooserDescriptor(true, true, false, false)

  private fun contributor(owns: (Path) -> Boolean): UniversalFileChooserContributor = object : UniversalFileChooserContributor {
    override val tabTitle: String = "Test"
    override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = emptyList()
    override fun ownsPath(path: Path): Boolean = owns(path)
  }

  private fun <T> NioFileTreeModel.onInvoker(task: () -> T): T =
    invoker.compute(task).blockingGet(30, TimeUnit.SECONDS)!!

  /**
   * The model must not probe a descriptor root with `Files.exists`: on Windows a read of
   * a `\\wsl.localhost\<distro>\` root starts the WSL machine of a stopped distribution
   * (IJPL-258774). Ownership by the contributor is the only check, so a root that does
   * not exist locally stays in the tree.
   */
  @Test
  fun `descriptor roots are filtered by contributor ownership, not by existence`() {
    val missing = root.resolve("missing")
    val existing = root.resolve("existing")
    Files.createDirectories(existing)
    val descriptor = descriptor().withPathRoots(missing, existing)
    val model = NioFileTreeModel(descriptor, contributor = contributor { it.startsWith(root) })
    try {
      val count = model.onInvoker { model.getChildCount(model.root) }
      assertThat(count).isEqualTo(2)
    }
    finally {
      Disposer.dispose(model)
    }
  }

  @Test
  fun `descriptor roots of another contributor are filtered out`() {
    val one = root.resolve("one")
    val two = root.resolve("two")
    Files.createDirectories(one)
    Files.createDirectories(two)
    val descriptor = descriptor().withPathRoots(one, two)
    val model = NioFileTreeModel(descriptor, contributor = contributor { false })
    try {
      val count = model.onInvoker { model.getChildCount(model.root) }
      assertThat(count).isEqualTo(0)
    }
    finally {
      Disposer.dispose(model)
    }
  }
}
