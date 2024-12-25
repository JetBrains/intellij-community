package com.intellij.microservices.url

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface UrlResolverFactory {
  fun forProject(project: Project): UrlResolver?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UrlResolverFactory> = ExtensionPointName.create("com.intellij.microservices.urlResolverFactory")
  }
}