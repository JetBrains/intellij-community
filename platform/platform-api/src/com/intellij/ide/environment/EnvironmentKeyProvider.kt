// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.environment

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/**
 * Used for registering environment keys.
 */
@ApiStatus.Experimental
interface EnvironmentKeyProvider {
  companion object {
    @JvmField
    @Internal
    val EP_NAME: ExtensionPointName<EnvironmentKeyProvider> = ExtensionPointName("com.intellij.environmentKeyProvider")
  }

  /**
   * Returns all keys that are used by a client of [EnvironmentService].
   * Each [EnvironmentKey] must be registered at least in one [EnvironmentKeyProvider.knownKeys].
   */
  val knownKeys: Map<EnvironmentKey, Supplier<@EnvironmentKeyDescription String>>

  /**
   * Returns all keys that are absolutely required for a project to be configured without interaction with the user.
   */
  suspend fun getRequiredKeys(project: Project): List<EnvironmentKey>

  @NlsContext(prefix = "environment.key.description")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.FUNCTION,
          AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
  annotation class EnvironmentKeyDescription
}