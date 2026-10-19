// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WatchRoots
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** The name of the file with the [default lines][AnalysisIgnoreDefaults.lines]. */
@ApiStatus.Internal
const val ANALYSIS_IGNORE_DEFAULTS_FILE_NAME: String = "default.analysisignore"

/**
 * The file in the config directory that holds the [default lines][AnalysisIgnoreDefaults.lines], in the `.analysisignore` format. Without the
 * file, the lines are the [built-in lines][AnalysisIgnoreDefaults.BUILT_IN_LINES], so that a later product version can change them. An empty
 * file holds no lines. The user edits the file in the editor, see [EditAnalysisIgnoreDefaultsAction].
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class AnalysisIgnoreDefaultsFile(coroutineScope: CoroutineScope) {

  @Volatile
  private var pathForTests: Path? = null

  /** The path of the file. The file can be absent. */
  val path: Path
    get() = pathForTests ?: PathManager.getOptionsDir().resolve(ANALYSIS_IGNORE_DEFAULTS_FILE_NAME)

  // The service reads the file at once, so that a read of the lines under the lock of the Workspace Model does no I/O.
  @Volatile
  private var currentLines: List<String> = readLines() ?: AnalysisIgnoreDefaults.BUILT_IN_LINES

  init {
    coroutineScope.launch(Dispatchers.IO) { watchFile() }
  }

  /** The supported patterns of the file, in the order of the file. The same list instance stays until the lines change. */
  val lines: List<String>
    get() = currentLines

  /** Returns `true` if [file] is the file at [path]. */
  fun isDefaultsFile(file: VirtualFile): Boolean =
    file.name == path.fileName.toString() && FileUtil.pathsEqual(file.path, FileUtil.toSystemIndependentName(path.toString()))

  /** Returns `true` if the file at [systemIndependentPath] is the file at [path]. */
  fun isDefaultsPath(systemIndependentPath: String): Boolean =
    FileUtil.pathsEqual(systemIndependentPath, FileUtil.toSystemIndependentName(path.toString()))

  /**
   * Reads the file again, and tells each [AnalysisIgnoreDefaultsListener] if the lines changed. An unsaved change in the editor counts, as for
   * a `.analysisignore` file in a project.
   */
  fun reload() {
    val newLines = readLines() ?: return
    if (newLines == currentLines) return
    currentLines = newLines
    ApplicationManager.getApplication().messageBus.syncPublisher(AnalysisIgnoreDefaultsListener.TOPIC).linesChanged()
  }

  /** Returns the text of a new file: a comment about the file and then the [built-in lines][AnalysisIgnoreDefaults.BUILT_IN_LINES]. */
  fun template(): String {
    val header = LangBundle.message("analysis.ignore.defaults.file.header", ANALYSIS_IGNORE_FILE_NAME)
      .lines()
      .joinToString("\n") { "# $it" }
    return header + "\n\n" + AnalysisIgnoreDefaults.BUILT_IN_LINES.joinToString("\n", postfix = "\n")
  }

  /** Reads the file from [path] until [disposable] goes away. */
  @TestOnly
  fun setPathForTests(path: Path, disposable: Disposable) {
    pathForTests = path
    reload()
    Disposer.register(disposable) {
      pathForTests = null
      reload()
    }
  }

  /**
   * Returns the lines of the file: the text of its document while the editor holds one, else the text on the disk. Returns the built-in
   * lines without the file, and `null` if the file cannot be read.
   */
  /**
   * Lets the file watcher report a change of the file outside the IDE. The watcher reports a change of a file in the VFS only. The
   * service loads the file and no other child of its directory, because a refresh reports each new child of a loaded directory. A file
   * that appears outside the IDE later is read at the next start.
   */
  private fun watchFile() {
    val path = path
    WatchRoots.getInstance().watch(path.parent, false)
    if (Files.exists(path)) LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
  }

  @Suppress("UseOptimizedEelFunctions") // The config directory is local.
  private fun readLines(): List<String>? {
    val path = path
    val text = documentText(path) ?: try {
      Files.readString(path)
    }
    catch (_: NoSuchFileException) {
      return AnalysisIgnoreDefaults.BUILT_IN_LINES
    }
    catch (e: IOException) {
      LOG.warn("Cannot read $path", e)
      return null
    }
    return supportedPatternsOf(path.toString(), text)
  }

  private fun documentText(path: Path): CharSequence? {
    val file = LocalFileSystem.getInstance()
      .findFileByPathIfCached(FileUtil.toSystemIndependentName(path.toString())) ?: return null
    if (!file.isValid) return null
    return FileDocumentManager.getInstance().getCachedDocument(file)?.immutableCharSequence
  }

  companion object {
    @JvmStatic
    fun getInstance(): AnalysisIgnoreDefaultsFile = ApplicationManager.getApplication().service()
  }
}

/** Gets a call when the [default lines][AnalysisIgnoreDefaultsFile.lines] change. */
@ApiStatus.Internal
fun interface AnalysisIgnoreDefaultsListener {
  fun linesChanged()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<AnalysisIgnoreDefaultsListener> = Topic(AnalysisIgnoreDefaultsListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

private val LOG = logger<AnalysisIgnoreDefaultsFile>()
