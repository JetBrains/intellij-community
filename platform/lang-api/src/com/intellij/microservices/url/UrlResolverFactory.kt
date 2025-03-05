package com.intellij.microservices.url

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

interface UrlResolverFactory {
  fun forProject(project: Project): UrlResolver?

  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<UrlResolverFactory> = ExtensionPointName.create("com.intellij.microservices.urlResolverFactory")
  }
}