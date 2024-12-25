// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.oas.serialization

import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@Deprecated("You must use generateOasDraft from OpenAPI Specifications plugin instead")
@ApiStatus.Internal
interface OasSerializationCompatibilityProvider {
  fun generateOasDraft(projectName: String, models: OpenApiSpecification): String
}

private val EP_NAME = ExtensionPointName.create<OasSerializationCompatibilityProvider>("com.intellij.microservices.oasSerializationCompatibilityProvider")

@Deprecated("You must use generateOasDraft from OpenAPI Specifications plugin instead")
@NlsSafe
@ApiStatus.Internal
fun generateOasDraft(projectName: String, models: OpenApiSpecification): String {
  return EP_NAME.extensionList.firstOrNull()?.generateOasDraft(projectName, models) ?: ""
}