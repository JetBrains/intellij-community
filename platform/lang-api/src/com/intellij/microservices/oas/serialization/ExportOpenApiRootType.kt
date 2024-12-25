package com.intellij.microservices.oas.serialization

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.RootType.findByClass
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.MicroservicesIcons
import javax.swing.Icon

internal class ExportOpenApiRootType : RootType("openApiExport", MicroservicesBundle.message("openapi.specifications.scratch.root")) {
  override fun substituteIcon(project: Project, file: VirtualFile): Icon = MicroservicesIcons.Openapi
}

internal fun exportOpenApiRootType(): RootType = findByClass(ExportOpenApiRootType::class.java)