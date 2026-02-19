package com.intellij.microservices.url

import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.microservices.url.references.UrlPksParser
import com.intellij.microservices.url.references.chopLeadingEmptyBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PartiallyKnownString

/**
 * Framework-specific implementations of [UrlPath] handling
 */
abstract class FrameworkUrlPathSpecification {
  /**
   * @return [UrlPathContext] declared by given [PsiElement] including all parent contexts
   * e.g. class context if [declaration] is a url-handler method
   */
  abstract fun getUrlPathContext(declaration: PsiElement): UrlPathContext

  open val parser: UrlPksParser
    get() = UrlPksParser(parseQueryParameters = false)

  fun parsePath(path: String?): UrlPath =
    path
      ?.let(::PartiallyKnownString)
      ?.let(parser::parseUrlPath)
      ?.urlPath
      ?.chopLeadingEmptyBlock()
    ?: UrlPath.EMPTY
}