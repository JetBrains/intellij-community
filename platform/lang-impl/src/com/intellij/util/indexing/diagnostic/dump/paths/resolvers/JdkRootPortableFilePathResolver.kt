package com.intellij.util.indexing.diagnostic.dump.paths.resolvers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

object JdkRootPortableFilePathResolver : PortableFilePathResolver {
  override fun findFileByPath(project: Project, portableFilePath: PortableFilePath): VirtualFile? {
    if (portableFilePath is PortableFilePath.JdkRoot) {
      return runReadAction {
        val jdk = ProjectJdkTable.getInstance().findJdk(portableFilePath.jdkName) ?: return@runReadAction null
        val rootType = if (portableFilePath.inClassFiles) OrderRootType.CLASSES else OrderRootType.SOURCES
        val roots = jdk.rootProvider.getFiles(rootType)
        roots.getOrNull(portableFilePath.jdkRootIndex)
      }
    }
    return null
  }

}