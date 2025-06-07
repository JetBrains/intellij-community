// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.jsonSchema.extension.JsonAnnotationsCollectionMode
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.util.concurrent.ConcurrentHashMap

private val JSON_SCHEMA_VALIDATION_MAP = Key.create<CachedValue<MutableMap<JsonSchemaObject, Boolean>>>("JSON_SCHEMA_VALIDATION_MAP")

internal fun getOrComputeAdapterValidityAgainstGivenSchema(value: JsonValueAdapter, schema: JsonSchemaObject): Boolean {
  val delegatePsi = value.delegate
  val cachedMap: MutableMap<JsonSchemaObject, Boolean> =
    CachedValuesManager.getManager(delegatePsi.project).getCachedValue(
      delegatePsi,
      JSON_SCHEMA_VALIDATION_MAP,
      CachedValueProvider {
        CachedValueProvider.Result.create(
          ConcurrentHashMap(),
          delegatePsi.manager.modificationTracker.forLanguage(delegatePsi.language),
          JsonSchemaService.Impl.get(delegatePsi.project)
        )
      },
      false
    )

  val cachedValue = cachedMap[schema]
  if (cachedValue != null) {
    return cachedValue
  }

  val checker = JsonSchemaAnnotatorChecker(
    value.delegate.project,
    JsonComplianceCheckerOptions(false,
                                 false,
                                 false,
                                 JsonAnnotationsCollectionMode.FIND_FIRST)
  )
  checker.checkByScheme(value, schema)
  val computedValue = checker.isCorrect

  cachedMap[schema] = computedValue
  return computedValue
}