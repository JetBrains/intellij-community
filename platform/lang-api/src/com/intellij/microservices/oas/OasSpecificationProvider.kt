package com.intellij.microservices.oas

import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides OpenAPI description for URL targets to complete request body parameters in HTTP Client and JavaScript.
 */
interface OasSpecificationProvider {

  fun getOasSpecification(urlTargetInfo: UrlTargetInfo): OpenApiSpecification?

  /**
   * @return a generated VirtualFile with the OpenAPI specification from [urlTargetInfo], wrapped into given [prefixes]
   * @param prefixes the list of schema property prefixes to wrap the specification
   */
  fun getOasSpecificationFile(urlTargetInfo: UrlTargetInfo, prefixes: List<String>): VirtualFile?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<OasSpecificationProvider> = ExtensionPointName.create(
      "com.intellij.microservices.oasSpecificationProvider")

    fun getOasSpecification(urlTargetInfo: UrlTargetInfo): OpenApiSpecification? =
      EP_NAME.lazySequence().firstNotNullOfOrNull { it.getOasSpecification(urlTargetInfo) }
  }
}