package com.intellij.microservices.url.parameters

import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.semantic.SemKey

@JvmField
val QUERY_PARAMETER_SEM_KEY: SemKey<QueryParameterSem> = SemKey.createKey("QueryParameter", RenameableSemElement.RENAMEABLE_SEM_KEY)

interface QueryParameterSem : RenameableSemElement {
  override val name: String

  val urlPathContext: UrlPathContext
}