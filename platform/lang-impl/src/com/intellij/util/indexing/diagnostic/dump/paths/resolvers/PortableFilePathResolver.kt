package com.intellij.util.indexing.diagnostic.dump.paths.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

interface PortableFilePathResolver {
  fun findFileByPath(project: Project, portableFilePath: PortableFilePath): VirtualFile?
}