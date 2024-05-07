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
  class Version(name: String, value: com.intellij.openapi.util.Version) : Feature.Custom<com.intellij.openapi.util.Version>(name, value) {
    override val valueType = IJFeatureValueType.Version
  }

  class Language(name: String, value: com.intellij.lang.Language) : Feature.Custom<com.intellij.lang.Language>(name, value) {
    override val valueType = IJFeatureValueType.Language
  }
}

@ApiStatus.Internal
class IJFeatureValueType {
  object Version : FeatureValueType.Custom<com.intellij.openapi.util.Version>({ VersionEventField(it, null) }) {
    override fun instantiate(name: String, value: com.intellij.openapi.util.Version): Feature {
      return IJFeature.Version(name, value)
    }
  }

  object Language : FeatureValueType.Custom<com.intellij.lang.Language>({ LanguageEventField(it, null) }) {
    override fun instantiate(name: String, value: com.intellij.lang.Language): Feature {
      return IJFeature.Language(name, value)
    }
  }
}

@ApiStatus.Internal
class IJFeatureDeclaration {
  companion object {
    fun version(name: String) = FeatureDeclaration(name, IJFeatureValueType.Version)

    fun language(name: String) = FeatureDeclaration(name, IJFeatureValueType.Language)
  }
}
