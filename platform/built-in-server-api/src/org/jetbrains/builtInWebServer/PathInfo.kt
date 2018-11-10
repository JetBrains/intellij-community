package org.jetbrains.builtInWebServer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PathInfo(val ioFile: Path?, file: VirtualFile?, val root: VirtualFile, moduleName: String? = null, val isLibrary: Boolean = false, val isRootNameOptionalInPath: Boolean = false) {
  var file: VirtualFile? = file
    private set
  
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
      builder.append(FileUtilRt.getRelativePath(relativeTo.path, ioFile!!.toString().replace(File.separatorChar, '/'), '/'))
    }
    else {
      builder.append(VfsUtilCore.getRelativePath(file!!, relativeTo, '/'))
    }
    return builder.toString()
  }
  
  fun getOrResolveVirtualFile(): VirtualFile? {
    return if (file == null) {
      val result = LocalFileSystem.getInstance().findFileByPath(ioFile!!.systemIndependentPath)
      file = result
      result
    }
    else {
      file
    }
  }

  /**
   * System-dependent path to file.
   */
  val filePath: String by lazy { ioFile?.toString() ?: FileUtilRt.toSystemDependentName(file!!.path) }

  val isValid: Boolean
    get() = if (ioFile == null) file!!.isValid else Files.exists(ioFile)

  val name: String
    get() = ioFile?.fileName?.toString() ?: file!!.name

  val fileType: FileType
    get() = if (ioFile == null) file!!.fileType else FileTypeManager.getInstance().getFileTypeByFileName(ioFile.fileName.toString())


  fun isDirectory(): Boolean = ioFile?.isDirectory() ?: file!!.isDirectory
}