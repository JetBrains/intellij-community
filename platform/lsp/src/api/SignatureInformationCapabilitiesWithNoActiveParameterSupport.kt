// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import org.eclipse.lsp4j.SignatureInformationCapabilities
import org.eclipse.lsp4j.jsonrpc.util.ToStringBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Adds the `noActiveParameterSupport` property (LSP 3.18), which the `lsp4j` library v1.0.0 doesn't support yet.
 *
 * TODO: Remove this class once the `lsp4j` lib is updated and supports `noActiveParameterSupport` out of the box.
 * (The class will become not compilable with the next `lsp4j` library update.)
 */
@ApiStatus.Experimental
class SignatureInformationCapabilitiesWithNoActiveParameterSupport : SignatureInformationCapabilities() {
  var noActiveParameterSupport: Boolean? = null

  override fun equals(other: Any?): Boolean =
    super.equals(other) &&
    noActiveParameterSupport == (other as SignatureInformationCapabilitiesWithNoActiveParameterSupport).noActiveParameterSupport

  override fun hashCode(): Int = 31 * super.hashCode() + noActiveParameterSupport.hashCode()

  override fun toString(): String =
    ToStringBuilder(this)
      .add("documentationFormat", documentationFormat)
      .add("parameterInformation", parameterInformation)
      .add("activeParameterSupport", activeParameterSupport)
      .add("noActiveParameterSupport", noActiveParameterSupport)
      .toString()
}
