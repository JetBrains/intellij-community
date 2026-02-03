// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.NotNull
import kotlin.math.roundToInt

internal class OriginalFileTypeCounter {
  private val fileTypeMappings: MutableMap<@NotNull FileType, MutableMap<@NotNull FileType, Int>> = mutableMapOf()

  fun recordOriginalFileType(effectiveType: FileType, originalType: FileType?) {
    val effectiveSchema = originalType ?: return

    val schemasForType = fileTypeMappings.getOrPut(effectiveType) { mutableMapOf() }
    val currentSchemaCount = schemasForType.getOrPut(effectiveSchema) { 0 }
    fileTypeMappings[effectiveType]!![effectiveSchema] = currentSchemaCount + 1
  }

  fun getFileTypeSchemaUsagePercentage(effectiveType: FileType): List<ObjectEventData> {
    val schemaCounts = fileTypeMappings[effectiveType].orEmpty()
    val allSchemasCount = schemaCounts.values.sum()
    val mappings = schemaCounts.map { (schema, count) ->
      val schemaPercentage = ((count.toDouble() / allSchemasCount) * 100).roundToInt()
      ObjectEventData(
        FileTypeUsagesCollector.TYPE_BY_EXTENSION with schema,
        FileTypeUsagesCollector.TYPE_BY_EXTENSION_PERCENT with schemaPercentage
      )
    }

    // If the only mapping is registered, it is the effective file type itself, useless to report
    return if (mappings.size <= 1)
      emptyList()
    else
      mappings
  }
}