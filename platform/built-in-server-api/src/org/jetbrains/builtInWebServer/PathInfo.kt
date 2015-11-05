package org.jetbrains.builtInWebServer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class PathInfo(val ioFile: File?, val file: VirtualFile?, val root: VirtualFile, moduleName: String? = null, val isLibrary: Boolean = false) {
  var moduleName: String? = moduleName
    set

  /**
   * URL path.
   */
  val path: String by lazy {
    val builder = StringBuilder()
    if (moduleName != null) {
      builder.append(moduleName).append('/')
    }

    if (isLibrary) {
      builder.append(root.name).append('/')
    }

    if (file == null) {
      builder.append(FileUtilRt.getRelativePath(root.path, FileUtilRt.toSystemIndependentName(ioFile!!.path), '/'))
    }
    else {
      builder.append(VfsUtilCore.getRelativePath(file, root, '/'))
    }
    builder.toString()
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