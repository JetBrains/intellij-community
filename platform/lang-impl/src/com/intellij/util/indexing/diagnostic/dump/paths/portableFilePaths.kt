// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.util.indexing.diagnostic.dump.paths.providers.*
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.*
import java.io.StringWriter
import java.io.Writer

object PortableFilePaths {

  private val PROVIDERS: List<PortableFilePathProvider> = listOf(
    JdkPortableFilePathProvider,
    LibraryRelativePortableFilePathProvider,
    IdePortableFilePathProvider,
    ProjectRelativePortableFilePathProvider
  )

  private val RESOLVERS: List<PortableFilePathResolver> = listOf(
    JdkRootPortableFilePathResolver,
    LibraryRootPortableFilePathResolver,
    ArchiveRootPortableFilePathResolver,
    IdeRootPortableFilePathResolver,
    ProjectRootPortableFilePathResolver,
    RelativePortableFilePathResolver
  )

  fun getPortableFilePath(virtualFile: VirtualFile, project: Project): PortableFilePath =
    PROVIDERS.asSequence().mapNotNull { it.getRelativePortableFilePath(project, virtualFile) }.firstOrNull()
    ?: PortableFilePath.AbsolutePath(virtualFile.url)

  fun findFileByPath(portableFilePath: PortableFilePath, project: Project): VirtualFile? =
    RESOLVERS.asSequence().mapNotNull { it.findFileByPath(project, portableFilePath) }.firstOrNull()
    ?: AbsolutePortableFilePathResolver.findFileByPath(project, portableFilePath)

  fun isSupportedFileSystem(virtualFile: VirtualFile): Boolean =
    virtualFile.isInLocalFileSystem || virtualFile.fileSystem is ArchiveFileSystem

}

object PortableFilePathsPersistence {

  private val jacksonMapper = jacksonObjectMapper()

  fun serializeToString(portableFilePath: PortableFilePath): String =
    jacksonMapper.writeValueAsString(portableFilePath)

  fun deserializeFromString(string: String): PortableFilePath =
    jacksonMapper.readValue(string)
}

class PortableFilesDumpCollector(
  private val project: Project,
  private val fileAdditionalInfoProvider: (VirtualFile) -> String? = { null }
) {
  private val files = arrayListOf<VirtualFile>()

  fun addFiles(files: Iterable<VirtualFile>) {
    this.files += files
  }

  fun addFile(file: VirtualFile) {
    this.files += file
  }

  fun dumpTo(writer: Writer, indent: String) {
    for (file in files) {
      val portable = PortableFilePaths.getPortableFilePath(file, project)
      if (file.exists() && file.isValid) {
        writer.append(indent).appendln(portable.presentablePath)
        val additionalInfo = fileAdditionalInfoProvider(file)
        if (additionalInfo != null) {
          writer.appendln(additionalInfo.lines().joinToString("\n") { indent.repeat(2) + it })
        }
      } else {
        writer.append("${indent}BROKEN: ").append(portable.presentablePath).appendln()
      }
    }
  }

  fun dumpToText() : String {
    val stringWriter = StringWriter()
    dumpTo(stringWriter, "")
    return stringWriter.toString()
  }
}
