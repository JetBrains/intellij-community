// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureValueType
import com.intellij.platform.ml.impl.logs.LanguageEventField
import com.intellij.platform.ml.impl.logs.VersionEventField
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IJFeature {
  class Version(name: String, value: com.intellij.openapi.util.Version, descriptionProvider: () -> String) : Feature.Custom<com.intellij.openapi.util.Version>(name, value, descriptionProvider) {
    override val valueType = IJFeatureValueType.Version
  }

  class Language(name: String, value: com.intellij.lang.Language, descriptionProvider: () -> String) : Feature.Custom<com.intellij.lang.Language>(name, value, descriptionProvider) {
    override val valueType = IJFeatureValueType.Language
  }
}

@ApiStatus.Internal
class IJFeatureValueType {
  object Version : FeatureValueType.Custom<com.intellij.openapi.util.Version>({ n, d -> VersionEventField(n, d) }) {
    override fun instantiate(name: String, value: com.intellij.openapi.util.Version, descriptionProvider: () -> String): Feature {
      return IJFeature.Version(name, value, descriptionProvider)
    }
  }

  object Language : FeatureValueType.Custom<com.intellij.lang.Language>({ n, d -> LanguageEventField(n, d) }) {
    override fun instantiate(name: String, value: com.intellij.lang.Language, descriptionProvider: () -> String): Feature {
      return IJFeature.Language(name, value, descriptionProvider)
    }
  }
}

@ApiStatus.Internal
class IJFeatureDeclaration {
  companion object {
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with the description")
    fun version(name: String) = FeatureDeclaration(name, IJFeatureValueType.Version)
    fun version(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, IJFeatureValueType.Version, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with the description")
    fun language(name: String) = FeatureDeclaration(name, IJFeatureValueType.Language)
    fun language(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, IJFeatureValueType.Language, descriptionProvider)
  }
}
