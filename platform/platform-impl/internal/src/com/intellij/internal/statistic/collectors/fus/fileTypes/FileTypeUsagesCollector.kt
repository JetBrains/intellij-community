// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeValidationRule
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.project.stateStore
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus

/**
 * Counts the content files of a project by file type and by extension.
 *
 * The counts come from [FileTypeIndex], so the type of a file is its indexed type, which the platform may detect by content.
 * The collector reads no file and walks no directory. A file that the index does not hold, for example a non-indexable file,
 * is not counted. Without [FileBasedIndex] the collector reports nothing.
 *
 * @see FileTypeValidationRule
 */
@ApiStatus.Internal
class FileTypeUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun collect(project: Project): Set<MetricEvent> {
    if (project.isDisposed || FileBasedIndex.getInstanceIfDefined() == null) return emptySet()

    val scope = ProjectScope.getContentScope(project)
    val fileTypeManager = FileTypeManager.getInstance()
    val countsByType = LinkedHashMap<FileType, TypeCount>()
    for (fileType in fileTypeManager.registeredFileTypes) {
      checkCanceled()
      // the block may run again after a write action, so it builds a fresh result each time
      val typeCount = smartReadAction(project) {
        if (project.isDisposed) null else countFilesOfType(project, fileType, scope)
      } ?: return emptySet()
      if (typeCount.count > 0) {
        countsByType[fileType] = typeCount
      }
    }
    return buildEvents(countsByType, fileTypeManager)
  }

  private fun countFilesOfType(project: Project, fileType: FileType, scope: GlobalSearchScope): TypeCount {
    val stateStore = project.stateStore
    val typeCount = TypeCount()
    FileTypeIndex.processFiles(fileType, { file ->
      ProgressManager.checkCanceled()
      // skip files from the .idea directory, otherwise 99% of projects would have XML and PLAIN_TEXT file types
      if (!stateStore.isProjectFile(file)) {
        typeCount.count++
        // null = no dot in the name; "" (name `b.`) is a real extension, as in the old content walk
        typeCount.countByExtension.merge(file.extension, 1, Int::plus)
      }
      true
    }, scope)
    return typeCount
  }

  private fun buildEvents(countsByType: Map<FileType, TypeCount>, fileTypeManager: FileTypeManager): Set<MetricEvent> {
    val fileTypeMappingsCounter = OriginalFileTypeCounter()
    val fileExtensionCounter = FileExtensionCounter()
    for ((fileType, typeCount) in countsByType) {
      for ((extension, count) in typeCount.countByExtension) {
        if (extension == null) {
          repeat(count) { fileTypeMappingsCounter.recordOriginalFileType(fileType, null) }
          continue
        }
        val fileTypeByExtension = fileTypeManager.getFileTypeByExtension(extension)
        repeat(count) {
          fileTypeMappingsCounter.recordOriginalFileType(fileType, fileTypeByExtension)
          fileExtensionCounter.recordOriginalFileType(extension)
        }
      }
    }

    val events = HashSet<MetricEvent>()
    for ((fileType, typeCount) in countsByType) {
      events.add(FILE_TYPE_IN_PROJECT.metric(
        EventFields.PluginInfoFromInstance.with(fileType),
        EventFields.FileType.with(fileType),
        COUNT.with(typeCount.count),
        FILE_TYPE_BY_EXTENSION_PERCENT.with(fileTypeMappingsCounter.getFileTypeSchemaUsagePercentage(fileType)),
      ))
    }
    for (extension in fileExtensionCounter.getFileExtensionsList()) {
      events.add(FILE_EXTENSION_IN_PROJECT.metric(
        FILE_EXTENSION.with(extension),
        COUNT.with(fileExtensionCounter.getFileExtensionCount(extension)),
        FILE_EXTENSION_BY_PERCENT.with(fileExtensionCounter.getFileExtensionUsagePercentage(extension)),
      ))
    }
    return events
  }

  private class TypeCount {
    var count: Int = 0
    val countByExtension: HashMap<String?, Int> = HashMap()
  }

  companion object {
    private val FILE_EXTENSION = EventFields.StringValidatedByCustomRule("file_extension", FileExtensionValidationRule::class.java)
    private val COUNT = EventFields.RoundedInt("count")

    // temporary is not collected
    @JvmField
    val SCHEMA: StringEventField = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator::class.java)

    @JvmField
    val PERCENT: IntEventField = EventFields.Int("percent")
    private val FILE_SCHEME_PERCENT = ObjectListEventField("file_schema", SCHEMA, PERCENT)

    @JvmField
    val TYPE_BY_EXTENSION: EventField<FileType?> = EventFields.FileType

    @JvmField
    val TYPE_BY_EXTENSION_PERCENT: IntEventField = EventFields.Int("percent")
    private val FILE_TYPE_BY_EXTENSION_PERCENT = ObjectListEventField("original_file_type", TYPE_BY_EXTENSION, TYPE_BY_EXTENSION_PERCENT)
    private val FILE_EXTENSION_BY_PERCENT = EventFields.Int("file_extension_percent")

    private val GROUP = EventLogGroup("file.types", 11)
    private val FILE_TYPE_IN_PROJECT = GROUP.registerVarargEvent(
      "file.type.in.project",
      EventFields.PluginInfoFromInstance,
      EventFields.FileType,
      COUNT,
      FILE_TYPE_BY_EXTENSION_PERCENT,
      FILE_SCHEME_PERCENT,
    )
    private val FILE_EXTENSION_IN_PROJECT = GROUP.registerVarargEvent(
      "file.extension.in.project",
      FILE_EXTENSION,
      COUNT,
      FILE_EXTENSION_BY_PERCENT,
    )
  }
}
