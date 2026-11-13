// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileSystems
import java.nio.file.Path
import javax.swing.Icon

/**
 * Supplies one tab of the universal file chooser.
 *
 * Implement this interface to show the files of an environment, for example WSL, Docker or a remote host.
 * Register the implementation in the [EP_NAME] extension point.
 * The chooser shows one tab for each contributor.
 * The tab shows the roots from [getRoots] as the top nodes of a file tree.
 *
 * A root with a [Root.path] is a real root.
 * The chooser lists its children through the NIO file system of the path.
 * A root without a path is a virtual root.
 * The chooser shows it as a placeholder and calls [mountVirtualRoot] when the user expands it.
 *
 * The chooser calls the `suspend` functions on a background thread.
 * The other functions can run on the EDT, so keep them fast and do no I/O in them.
 */
@ApiStatus.Experimental
interface UniversalFileChooserContributor {
  /**
   * The availability of a real root. [getMountStatus] returns it.
   */
  enum class MountStatus {
    /** The root is always available. */
    Permanent,

    /** The root is available now. It can become [Unmounted] later. */
    Mounted,

    /** The root is not available. The chooser calls [mount] before it lists the children. */
    Unmounted
  }

  companion object {
    /** The extension point of the contributors. */
    @JvmField
    val EP_NAME: ExtensionPointName<UniversalFileChooserContributor> = ExtensionPointName("com.intellij.universalFileChooserContributor")

    /**
     * Makes roots from the root directories of the default file system.
     *
     * Use it in [getRoots] when the environment shows its roots in the default file system.
     *
     * @param predicate selects the root directories to keep. Usually, it is [ownsPath].
     * @return a real root for each selected root directory, made with [asDefaultRoot].
     */
    fun getFilteredSystemRoots(predicate: (Path) -> Boolean): List<Root> {
      return FileSystems.getDefault().getRootDirectories().filter(predicate).map { asDefaultRoot(it) }
    }

    /**
     * Converts a path to root with the default presentation.
     *
     * @param path the root directory.
     * @return a root that uses the file name of [path] as the ID and as the presentable name.
     *   When [path] has no file name, the root uses the path text.
     */
    fun asDefaultRoot(path: Path): Root {
      val name = path.fileName?.toString() ?: path.toString()
      return Root(
        name,
        Presentation(name, ),
        path)
    }
  }

  /**
   * The title of the tab of this contributor.
   *
   * The chooser finds the tab by this title. Give each contributor a different title.
   */
  @get:Nls(capitalization = Nls.Capitalization.Title)
  val tabTitle: String

  /**
   * Returns the roots that the tab shows.
   *
   * The chooser calls it when it makes the tab.
   * It calls it again after a mount and after a change of a mount status.
   * The chooser shows a loading text and the progress that the function reports.
   * When the function throws an exception, the chooser shows the error and a retry link.
   *
   * @return the real roots and the virtual roots of the tab, in the display order.
   */
  suspend fun getRoots(): List<Root>

  /**
   * Returns the roots of the environment of [path].
   *
   * The chooser calls it instead of [getRoots] when it limits the roots to the environment of the project.
   * The default implementation returns the roots from [getRoots].
   *
   * @param path the directory of the project.
   * @return the roots of the environment that contains [path]. Return an empty list when [path] is not in this environment.
   */
  suspend fun getFilteredRoots(path: Path): List<Root> = getRoots()

  /**
   * Tells if this contributor shows [path].
   *
   * The chooser uses it to select the tab of a path, for example the project directory or the last opened path.
   * The first contributor that owns a path gets it. Do not claim the paths of other environments.
   * The chooser can call it on the EDT. Keep it fast and do no I/O.
   *
   * @param path an absolute path from any file system.
   * @return `true` when [path] belongs to this contributor.
   */
  fun ownsPath(path: Path): Boolean

  /**
   * Returns the availability of a real root.
   *
   * The chooser calls it when it loads the roots.
   * Then it polls each real root every few seconds and updates the tree when the status changes.
   * The chooser does not expand a root with the [MountStatus.Unmounted] status before [mount] completes.
   *
   * @param path the [Root.path] of a real root.
   * @return the status of the root. The default implementation returns [MountStatus.Permanent].
   */
  suspend fun getMountStatus(path: Path): MountStatus = MountStatus.Permanent

  /**
   * Makes a root with the [MountStatus.Unmounted] status available.
   *
   * The chooser calls it when the user expands an unmounted root.
   * Then the chooser loads the roots again and selects [path].
   * When the function throws an exception, the chooser shows the message of the exception on the root.
   * The default implementation does nothing.
   *
   * @param path the [Root.path] of the root to mount.
   */
  suspend fun mount(path: Path) {}

  /**
   * Makes a virtual root available.
   *
   * The chooser calls it when the user expands a root without a [Root.path].
   * Then the chooser loads the roots again with [getRoots].
   * After this call, [getRoots] must return a real root in place of [virtualRoot].
   *
   * @param virtualRoot a root from [getRoots] that has no path. Use [Root.id] to find its environment.
   * @return the path that the chooser selects after the load, or `null` to select nothing.
   *   The default implementation returns `null`.
   */
  suspend fun mountVirtualRoot(virtualRoot: Root): Path? = null

  /**
   * Returns the adapter that sends the file changes to the tree.
   *
   * @return the adapter, or `null` when the environment cannot watch files.
   */
  @ApiStatus.Experimental
  fun getFileWatcherAdapter(): FileWatcherAdapter? = null

  /**
   * A top node of the tab.
   *
   * @param id the identifier of the root. Use it in [mountVirtualRoot] to find the environment of a virtual root.
   * @param presentation the name, the icon and the comment of the node.
   * @param path the root directory of a real root, or `null` for a virtual root.
   */
  data class Root(
    val id: String,
    val presentation: Presentation,
    val path : Path? = null
  )

  /**
   * The appearance of a root node in the tree.
   *
   * @param presentableName the name that the node shows.
   * @param icon the icon of the node, or `null` for the default icon.
   * @param comment the text after the name, or `null` for no comment.
   */
  data class Presentation(
    @get:Nls val presentableName: String,
    val icon: Icon? = null,
    @get:Nls val comment: String? = null,
  )

  /**
   * Returns the presentation of a real root.
   *
   * The chooser calls it for each real root when it loads the roots.
   *
   * @param path the [Root.path] of a real root.
   * @return the presentation of the root, or `null` when [Root.presentation] is sufficient.
   *   The default implementation returns `null`.
   */
  suspend fun getPresentation(path: Path): Presentation? = null

  /**
   * Returns the name of the node of [path] in the tree.
   *
   * @param path a path that this contributor owns.
   * @return the name to show, or `null` to show the default name.
   *   The default implementation returns the last element of [path].
   */
  fun getFileName(path: Path): String? = path.fileName?.toString()

  /**
   * Converts [path] to the text of the path field.
   *
   * Return a text that [parsePresentablePath] can convert back to [path].
   *
   * @param path a path that this contributor owns.
   * @return the text that the user sees and edits. The default implementation returns `path.toString()`.
   */
  fun getPresentablePath(path: Path): @NlsSafe String = path.toString()

  /**
   * Converts the text of the path field to a path.
   *
   * The chooser calls it on a background thread when the user types a path.
   * The chooser can also give the text from the tab of another contributor.
   * Then the chooser uses [ownsPath] to examine the result.
   *
   * @param text the text that the user typed.
   * @return the path, or `null` when [text] is not a valid path for this contributor.
   *   The default implementation uses [Path.of].
   */
  fun parsePresentablePath(text: String): Path? = runCatching { Path.of(text) }.getOrNull()

  /**
   * Returns the desktop directory of the environment.
   *
   * The chooser calls it on a background thread when it makes the tab.
   * The "Desktop Directory" action of the chooser goes to this directory.
   *
   * @return the desktop directory, or `null` when the environment has no desktop directory.
   *   The default implementation returns `null`.
   */
  fun getDesktopPath(): Path? = null

  /**
   * Returns the text that the tab shows while [getRoots] runs.
   *
   * @return the loading text, or `null` for the default text.
   */
  fun getCustomLoadingText(): @Nls String? = null

  /**
   * Returns the text that the tree shows when the tab has no roots.
   *
   * @return the empty text, or `null` for the default text.
   */
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


