// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.NotNull
import kotlin.math.roundToInt

internal class FileTypeSchemaCounter {
  private val NO_SCHEMA = ""
  private val fileTypeSchemaData: MutableMap<@NotNull FileType, MutableMap<String, Int>> = mutableMapOf()

  fun recordFileTypeWithSchema(effectiveType: FileType, schemaType: String?) {
    val effectiveSchema = schemaType ?: NO_SCHEMA

    val schemasForType = fileTypeSchemaData.getOrPut(effectiveType) { mutableMapOf() }
    val currentSchemaCount = schemasForType.getOrPut(effectiveSchema) { 0 }
    fileTypeSchemaData[effectiveType]!![effectiveSchema] = currentSchemaCount + 1
  }

  fun getFileTypeSchemaUsagePercentage(effectiveType: FileType): List<ObjectEventData> {
    val schemaCounts = fileTypeSchemaData[effectiveType].orEmpty()
    val allSchemasCount = schemaCounts.values.sum()
    return schemaCounts.map { (schema, count) ->
      val schemaPercentage = ((count.toDouble() / allSchemasCount) * 100).roundToInt()
      ObjectEventData(
        FileTypeUsagesCollector.SCHEMA with schema,
        FileTypeUsagesCollector.PERCENT with schemaPercentage
      )
    }
  }
}