package org.jetbrains.builtInWebServer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class PathInfo(val ioFile: File?, val file: VirtualFile?, val root: VirtualFile, moduleName: String? = null, val isLibrary: Boolean = false, val isRootNameOptionalInPath: Boolean = false) {
  var moduleName: String? = moduleName
    set

  /**
   * URL path.
   */
  val path: String by lazy {
    buildPath(true)
  }

  val rootLessPathIfPossible: String? by lazy {
    if (isRootNameOptionalInPath) buildPath(false) else null
  }

  private fun buildPath(useRootName: Boolean): String {
    val builder = StringBuilder()
    if (moduleName != null) {
      builder.append(moduleName).append('/')
    }

    if (isLibrary) {
      builder.append(root.name).append('/')
    }

    val relativeTo = if (useRootName) root else root.parent ?: root
    if (file == null) {
      builder.append(FileUtilRt.getRelativePath(relativeTo.path, FileUtilRt.toSystemIndependentName(ioFile!!.path), '/'))
    }
    else {
      builder.append(VfsUtilCore.getRelativePath(file, relativeTo, '/'))
    }
    return builder.toString()
  }

  /**
   * System-dependent path to file.
   */
  val filePath: String by lazy { if (ioFile == null) FileUtilRt.toSystemDependentName(file!!.path) else ioFile.path }

  val isValid: Boolean
    get() = if (ioFile == null) file!!.isValid else ioFile.exists()

  val name: String
    get() = if (ioFile == null) file!!.name else ioFile.name

  val fileType: FileType
    get() = if (ioFile == null) file!!.fileType else FileTypeManager.getInstance().getFileTypeByFileName(ioFile.name)


  fun isDirectory(): Boolean = if (ioFile == null) file!!.isDirectory else ioFile.isDirectory
}