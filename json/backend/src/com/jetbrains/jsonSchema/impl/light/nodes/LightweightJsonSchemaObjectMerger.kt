// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.MergedJsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMerger
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

internal object LightweightJsonSchemaObjectMerger : JsonSchemaObjectMerger {
  override fun mergeObjects(base: JsonSchemaObject, other: JsonSchemaObject, pointTo: JsonSchemaObject): JsonSchemaObject {
    ProgressManager.checkCanceled()
    if (base === other) {
      return base
    }
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.SchemaMerged)
    return MergedJsonSchemaObjectView(base, other, pointTo)
  }
}

internal fun <T> MergedJsonSchemaObject.mergeLists(memberReference: JsonSchemaObject.() -> List<T>?): List<T>? {
  val first = base.memberReference()
  val second = other.memberReference()

  if (first.isNullOrEmpty()) return second
  if (second.isNullOrEmpty()) {
    return first
  }
  ProgressManager.checkCanceled()
  return ContainerUtil.concat(first, second)
}

internal fun <K, V> MergedJsonSchemaObject.mergeMaps(memberReference: JsonSchemaObject.() -> Map<K, V>?): Map<K, V>? {
  val first = base.memberReference()
  val second = other.memberReference()

  if (first.isNullOrEmpty()) return second
  if (second.isNullOrEmpty()) {
    return first
  }
  val merged = first.toMutableMap()
  ProgressManager.checkCanceled()
  merged.putAll(second)
  return merged.toImmutableMap()
}

internal fun <T> mergeSets(first: Set<T>?, second: Set<T>?): Set<T>? {
  if (first.isNullOrEmpty()) return second
  if (second.isNullOrEmpty()) {
    return first
  }
  val merged = first.toMutableSet()
  ProgressManager.checkCanceled()
  merged.addAll(second)
  return merged.toImmutableSet()
}

internal fun booleanOr(base: JsonSchemaObject, other: JsonSchemaObject, memberReference: JsonSchemaObject.() -> Boolean): Boolean {
  val first = base.memberReference()
  if (first) return true
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun booleanAnd(base: JsonSchemaObject, other: JsonSchemaObject, memberReference: JsonSchemaObject.() -> Boolean): Boolean {
  val first = base.memberReference()
  if (!first) return false
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun booleanAndNullable(base: JsonSchemaObject, other: JsonSchemaObject, memberReference: JsonSchemaObject.() -> Boolean?): Boolean? {
  val first = base.memberReference()
  if (first == false) return false
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun <V> booleanOrWithArgument(base: JsonSchemaObject, other: JsonSchemaObject, memberReference: JsonSchemaObject.(V) -> Boolean, argument: V): Boolean {
  val first = base.memberReference(argument)
  if (first) return true
  ProgressManager.checkCanceled()
  return other.memberReference(argument)
}

internal fun <T> baseIfConditionOrOther(
  base: JsonSchemaObject,
  other: JsonSchemaObject,
  memberReference: JsonSchemaObject.() -> T,
  condition: (T) -> Boolean,
): T {
  ProgressManager.checkCanceled()
  val baseResult = base.memberReference()
  ProgressManager.checkCanceled()
  if (condition(baseResult)) return baseResult
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun <T, V> baseIfConditionOrOtherWithArgument(
  base: JsonSchemaObject,
  other: JsonSchemaObject,
  memberReference: JsonSchemaObject.(V) -> T,
  argument: V,
  condition: (T) -> Boolean,
): T {
  ProgressManager.checkCanceled()
  val baseResult = base.memberReference(argument)
  ProgressManager.checkCanceled()
  if (condition(baseResult)) return baseResult
  ProgressManager.checkCanceled()
  return other.memberReference(argument)
}

internal fun String?.isNotBlank(): Boolean {
  return !this.isNullOrBlank()
}

internal fun Any?.isNotNull(): Boolean {
  return this != null
}