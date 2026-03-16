// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.util.indexing.diagnostic.dump.paths.providers.IdePortableFilePathProvider
import com.intellij.util.indexing.diagnostic.dump.paths.providers.JdkPortableFilePathProvider
import com.intellij.util.indexing.diagnostic.dump.paths.providers.LibraryRelativePortableFilePathProvider
import com.intellij.util.indexing.diagnostic.dump.paths.providers.PortableFilePathProvider
import com.intellij.util.indexing.diagnostic.dump.paths.providers.ProjectRelativePortableFilePathProvider
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.AbsolutePortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.ArchiveRootPortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.IdeRootPortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.JdkRootPortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.LibraryRootPortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.PortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.ProjectRootPortableFilePathResolver
import com.intellij.util.indexing.diagnostic.dump.paths.resolvers.RelativePortableFilePathResolver

object PortableFilePaths {

  private val PROVIDERS: List<PortableFilePathProvider> = listOf(
    JdkPortableFilePathProvider,
    LibraryRelativePortableFilePathProvider,
    ProjectRelativePortableFilePathProvider,
    IdePortableFilePathProvider
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

fun PortableFilePath.hasPresentablePathMatching(pattern: String): Boolean =
  when {
    pattern.startsWith("*") -> presentablePath.endsWith(pattern.substring(1))
    pattern.endsWith("*") -> presentablePath.startsWith(pattern.dropLast(1))
    else -> presentablePath == pattern
  }