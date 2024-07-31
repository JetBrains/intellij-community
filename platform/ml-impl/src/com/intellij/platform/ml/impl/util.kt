// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.platform.ml.feature.FeatureDeclaration
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

/**
 * Extracts all declarations of [FeatureDeclaration] from [declarationContainer]'s declaration
 *
 * The common usage is to write extractFieldsAsFeatureDeclarations(Companion) when the companion
 * object contains the declarations.
 *
 * @param declarationContainer Object whose declaration contains features' descriptions
 * @param extractors Types of properties that are desired to be extracted from.
 */
@ApiStatus.Internal
inline fun <reified T : Any> extractFieldsAsFeatureDeclarations(declarationContainer: T,
                                                                extractors: Collection<FeatureDeclarationsExtractor> = listOf(FeatureDeclarationsExtractor.OfProperty,
                                                                                                                              FeatureDeclarationsExtractor.ofIterable,
                                                                                                                              FeatureDeclarationsExtractor.ofMapValues)
): Set<FeatureDeclaration<*>> {
  return T::class.declaredMemberProperties
    .flatMap { property ->
      property.isAccessible = true
      val propertyValue = property.call(declarationContainer)
      val returnTypeClassifier = property.returnType.classifier
      if (returnTypeClassifier !is KClass<*>) return@flatMap emptyList()
      val extractedDeclarationsByExtractor = extractors.associateWith { it.extract(returnTypeClassifier, propertyValue) }
      val workedExtractors = extractedDeclarationsByExtractor.entries.filter { it.value != null }
      if (workedExtractors.isEmpty()) return@flatMap emptyList()
      require(workedExtractors.size == 1) { "Ambiguous extraction of $property: ${workedExtractors}" }
      return@flatMap requireNotNull(workedExtractors.first().value)
    }
    .toSet()
}

@ApiStatus.Internal
fun interface FeatureDeclarationsExtractor {
  fun extract(returnClass: KClass<*>, propertyValue: Any?): Collection<FeatureDeclaration<*>>?

  companion object {
    val OfProperty = FeatureDeclarationsExtractor { returnClass, propertyValue ->
      if (returnClass.isSubclassOf(FeatureDeclaration::class)) listOf(propertyValue as FeatureDeclaration<*>) else null
    }

    val ofIterable = FeatureDeclarationsExtractor { returnClass, propertyValue ->
      if (returnClass.isSubclassOf(Iterable::class)) (propertyValue as Iterable<*>).filterIsInstance(FeatureDeclaration::class.java) else null
    }

    val ofMapValues = FeatureDeclarationsExtractor { returnClass, propertyValue ->
      val mapValues = if (returnClass.isSubclassOf(Map::class)) (propertyValue as Map<*, *>).values else return@FeatureDeclarationsExtractor null
      val simpleValues = mapValues.filterIsInstance(FeatureDeclaration::class.java)
      val iterValues = mapValues.filterIsInstance(Iterable::class.java).flatMap { it.filterIsInstance(FeatureDeclaration::class.java) }
      simpleValues + iterValues
    }
  }
}
