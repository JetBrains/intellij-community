// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.apiPlatform

import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.FeatureValueType
import org.jetbrains.annotations.ApiStatus

/**
 * Prints code-like features representation, when they have to be logged for an API user's
 * convenience (so they can just copy-paste the logged features into their code).
 */
@ApiStatus.Internal
class CodeLikePrinter {
  private val <T : Enum<*>> FeatureValueType.Enum<T>.codeLikeType: String
    get() = this.enumClass.name

  private fun <T> FeatureValueType<T>.makeCodeLikeString(name: String): String = when (this) {
    FeatureValueType.Boolean -> "FeatureDeclaration.boolean(\"$name\")"
    FeatureValueType.Class -> "FeatureDeclaration.aClass(\"$name\")"
    FeatureValueType.Double -> "FeatureDeclaration.double(\"$name\")"
    is FeatureValueType.Enum<*> -> "FeatureDeclaration.enum<${this.codeLikeType}>(\"$name\")"
    FeatureValueType.Float -> "FeatureDeclaration.float(\"${name}\")"
    FeatureValueType.Int -> "FeatureDeclaration.int(\"${name}\")"
    FeatureValueType.Long -> "FeatureDeclaration.long(\"${name}\")"
    is FeatureValueType.Nullable<*> -> "${this.baseType.makeCodeLikeString(name)}.nullable()"
    is FeatureValueType.Categorical -> {
      val possibleValuesSerialized = possibleValues.joinToString(", ") { "\"$it\"" }
      "FeatureDeclaration.categorical(\"$name\", setOf(${possibleValuesSerialized}))"
    }
    FeatureValueType.Version -> "FeatureDeclaration.version(\"${name}\")"
    FeatureValueType.Language -> "FeatureDeclaration.language(\"${name}\")"
  }

  fun <T> printCodeLikeString(featureDeclaration: FeatureDeclaration<T>): String {
    return featureDeclaration.type.makeCodeLikeString(featureDeclaration.name)
  }

  fun printCodeLikeString(featureDeclarations: Collection<FeatureDeclaration<*>>): String {
    return featureDeclarations.joinToString(", ") { printCodeLikeString(it) }
  }
}
