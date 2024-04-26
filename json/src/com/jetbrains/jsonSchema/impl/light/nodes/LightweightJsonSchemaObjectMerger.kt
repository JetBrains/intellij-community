// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMerger
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

internal object LightweightJsonSchemaObjectMerger : JsonSchemaObjectMerger {
  override fun mergeObjects(base: JsonSchemaObject, other: JsonSchemaObject, pointTo: JsonSchemaObject): JsonSchemaObject {
    ProgressManager.checkCanceled()
    return MergedJsonSchemaObjectView(base, other, pointTo)
  }
}

internal fun <T> MergedJsonSchemaObjectView.mergeLists(memberReference: JsonSchemaObject.() -> List<T>?): List<T>? {
  val first = base.memberReference()
  val second = other.memberReference()

  if (first.isNullOrEmpty()) return second
  if (second.isNullOrEmpty()) {
    return first
  }
  ProgressManager.checkCanceled()
  return ContainerUtil.concat(first, second)
}

internal fun <K, V> MergedJsonSchemaObjectView.mergeMaps(memberReference: JsonSchemaObject.() -> Map<K, V>?): Map<K, V>? {
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

internal fun MergedJsonSchemaObjectView.booleanOr(memberReference: JsonSchemaObject.() -> Boolean): Boolean {
  val first = base.memberReference()
  if (first) return true
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun MergedJsonSchemaObjectView.booleanAnd(memberReference: JsonSchemaObject.() -> Boolean): Boolean {
  val first = base.memberReference()
  if (!first) return false
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun <V> MergedJsonSchemaObjectView.booleanOrWithArgument(memberReference: JsonSchemaObject.(V) -> Boolean, argument: V): Boolean {
  val first = base.memberReference(argument)
  if (first) return true
  ProgressManager.checkCanceled()
  return other.memberReference(argument)
}

internal fun <T> MergedJsonSchemaObjectView.baseIfConditionOrOther(memberReference: JsonSchemaObject.() -> T,
                                                                   condition: (T) -> Boolean): T {
  ProgressManager.checkCanceled()
  val baseResult = base.memberReference()
  ProgressManager.checkCanceled()
  if (condition(baseResult)) return baseResult
  ProgressManager.checkCanceled()
  return other.memberReference()
}

internal fun <T, V> MergedJsonSchemaObjectView.baseIfConditionOrOtherWithArgument(memberReference: JsonSchemaObject.(V) -> T,
                                                                                  argument: V,
                                                                                  condition: (T) -> Boolean): T {
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