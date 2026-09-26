// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileSystems
import java.nio.file.Path
import javax.swing.Icon

@ApiStatus.Internal
interface UniversalFileChooserContributor {
  enum class MountStatus {
    Permanent, Mounted, Unmounted
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UniversalFileChooserContributor> = ExtensionPointName("com.intellij.universalFileChooserContributor")

    fun getFilteredSystemRoots(predicate: (Path) -> Boolean): List<Root> {
      return FileSystems.getDefault().getRootDirectories().filter(predicate).map { asDefaultRoot(it) }
    }

    fun asDefaultRoot(path: Path): Root {
      val name = path.fileName?.toString() ?: path.toString()
      return Root(
        name,
        Presentation(name, ),
        path)
    }
  }

  @get:Nls(capitalization = Nls.Capitalization.Title)
  val tabTitle: String

  suspend fun getRoots(): List<Root>

  suspend fun getFilteredRoots(path: Path): List<Root> = getRoots()

  fun ownsPath(path: Path): Boolean

  suspend fun getMountStatus(path: Path): MountStatus = MountStatus.Permanent

  suspend fun mount(path: Path) {}

  suspend fun mountVirtualRoot(virtualRoot: Root): Path? = null

  fun getFileWatcherAdapter(): FileWatcherAdapter? = null

  data class Root(
    val id: String,
    val presentation: Presentation,
    val path : Path? = null
  )

  data class Presentation(
    @get:Nls val presentableName: String,
    val icon: Icon? = null,
    @get:Nls val comment: String? = null,
  )

  suspend fun getPresentation(path: Path): Presentation? = null

  fun getFileName(path: Path): String? = path.fileName?.toString()

  fun getPresentablePath(path: Path): @NlsSafe String = path.toString()

  fun parsePresentablePath(text: String): Path? = runCatching { Path.of(text) }.getOrNull()

  fun getDesktopPath(): Path? = null

  fun getCustomLoadingText(): @Nls String? = null

  fun getNoEntriesText(): @Nls String? = null
}

@ApiStatus.Internal
class SingleRootContributor(
  private val delegate: UniversalFileChooserContributor,
  private val rootPath: Path,
) : UniversalFileChooserContributor by delegate {
  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = delegate.getFilteredRoots(rootPath)
}

@ApiStatus.Internal
fun Collection<UniversalFileChooserContributor>.findOwner(path: Path): UniversalFileChooserContributor? =
  firstOrNull { runCatching { it.ownsPath(path) }.getOrDefault(false) }


