// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "HardCodedStringLiteral")

package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.asSafely
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedEvent
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.JsonSharedIndexDiagnosticEvent
import com.intellij.util.indexing.diagnostic.dto.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.lang.annotations.Language
import java.util.*

fun createAggregateHtml(
  target: Appendable,
  projectName: String,
  diagnostics: List<IndexDiagnosticDumper.ExistingDiagnostic>,
  sharedIndexEvents: List<JsonSharedIndexDiagnosticEvent>,
  changedFilesPushEvents: List<ChangedFilesPushedEvent>
) {
  target.appendHTML().html {
    head {
      title("Indexing diagnostics of '$projectName'")
      style {
        unsafe {
          +CSS_STYLE
        }
      }
      script {
        unsafe {
          +LINKABLE_TABLE_ROW_SCRIPT
        }
      }
    }
    body {
      div(classes = "aggregate-report-content") {
        h1("Project name")
        text(projectName)

        div {
          h1("Indexing history")
          table(classes = "centered-text") {
            unsafe {
              +"<caption style=\"caption-side: bottom; text-align: right; font-size: 14px\">Hover for details</caption>"
            }
            thead {
              tr {
                th("Time") {
                  colSpan = "6"
                }
                th("Files") {
                  colSpan = "5"
                }
                th("Type") {
                  rowSpan = "2"
                }
              }
              tr {
                th("Started")
                th("Total")
                th("Scanning")
                th("Indexing")
                th("Content loading")
                th("Finished")
                th("Scanned")
                th("Shared indexes (w/o content loading)")
                th("Scheduled for indexing")
                th("Shared indexes (content loaded)")
                th("Total indexed (shared indexes included)")
              }
            }
            tbody {
              for (diagnostic in diagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }) {
                tr(classes = "linkable-table-row") {
                  attributes["href"] = diagnostic.htmlFile.fileName.toString()
                  // Time section.
                  td {
                    if (diagnostic.indexingTimes.indexingReason != null) {
                      strong(diagnostic.indexingTimes.indexingReason)
                      br()
                    }
                    text(diagnostic.indexingTimes.updatingStart.presentableLocalDateTime())
                  }
                  td(diagnostic.indexingTimes.totalUpdatingTime.presentableDuration())
                  td(diagnostic.indexingTimes.scanFilesTime.presentableDuration())
                  td(diagnostic.indexingTimes.indexingTime.presentableDuration())
                  td(diagnostic.indexingTimes.contentLoadingVisibleTime.presentableDuration())
                  td {
                    if (diagnostic.indexingTimes.wasInterrupted) {
                      strong("Cancelled")
                      br()
                    }
                    text(diagnostic.indexingTimes.updatingEnd.presentableLocalDateTime())
                  }

                  // Files section.
                  val fileCount = diagnostic.fileCount
                  td(fileCount?.numberOfScannedFiles?.toString() ?: NOT_APPLICABLE)
                  td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringScan?.toString() ?: NOT_APPLICABLE)
                  td(fileCount?.numberOfFilesScheduledForIndexingAfterScan?.toString() ?: NOT_APPLICABLE)
                  td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage?.toString() ?: NOT_APPLICABLE)
                  td(fileCount?.numberOfFilesIndexedWithLoadingContent?.toString() ?: NOT_APPLICABLE)

                  //Indexing type section
                  td(diagnostic.indexingTimes.scanningType.name.lowercase(Locale.ENGLISH).replace('_', ' '))
                }
              }
            }
          }
        }

        if (sharedIndexEvents.isNotEmpty()) {
          val indexIdToEvents = sharedIndexEvents.groupBy { it.chunkUniqueId }
          div {
            h1("Shared Indexes")
            table {
              thead {
                tr {
                  th("Time")
                  th("Kind")
                  th("Name")
                  th("Size")
                  th("Download time")
                  th("Download speed")
                  th("Status")
                  th("ID")
                  th("Generation time")
                }
              }
              tbody {
                for (event in sharedIndexEvents.filterIsInstance<JsonSharedIndexDiagnosticEvent.Downloaded>().sortedByDescending { it.time.instant }) {
                  val events = indexIdToEvents.getOrDefault(event.chunkUniqueId, emptyList())
                  val lastAttach = events.filterIsInstance<JsonSharedIndexDiagnosticEvent.Attached>().maxByOrNull { it.time.instant }
                                   ?: continue
                  tr {
                    td(event.time.presentableLocalDateTime())
                    td(lastAttach.kind)
                    td((lastAttach as? JsonSharedIndexDiagnosticEvent.Attached.Success)?.indexName ?: NOT_APPLICABLE)
                    td(event.packedSize.presentableSize())
                    td(event.downloadTime.presentableDuration())
                    td(event.downloadSpeed.presentableSpeed())
                    td(event.finishType + ((lastAttach as? JsonSharedIndexDiagnosticEvent.Attached.Success)?.let {
                      " FB: ${it.fbMatch.presentablePercentages()}, Stub: ${it.stubMatch.presentablePercentages()}"
                    } ?: " Incompatible"))
                    td(event.chunkUniqueId)
                    td(event.generationTime?.presentableLocalDateTime() ?: "unknown")
                  }
                }
              }
            }
          }
        }

        if (changedFilesPushEvents.isNotEmpty()) {
          div {
            h1("Scanning to push properties of changed files")
            table {
              thead {
                tr {
                  th("Time")
                  th("Reason")
                  th("Full duration")
                  th("Is cancelled")
                  th("Number")
                }
              }
              tbody {
                val eventsToUnify = mutableListOf<ChangedFilesPushedEvent>()
                for (event in changedFilesPushEvents.sortedByDescending { it.startTime.instant }) {
                  if (canUnify(event, eventsToUnify)) {
                    eventsToUnify.add(event)
                  }
                  else {
                    printUnified(eventsToUnify)
                    eventsToUnify.clear()
                    printEvent(event)
                  }
                }
                printUnified(eventsToUnify)
              }
            }
          }
        }
      }
    }
  }
}

internal fun createAggregateActivityHtml(
  target: Appendable,
  projectName: String,
  diagnostics: List<IndexDiagnosticDumper.ExistingIndexingActivityDiagnostic>,
  sharedIndexEvents: List<JsonSharedIndexDiagnosticEvent>,
  changedFilesPushEvents: List<ChangedFilesPushedEvent>
) {
  target.appendHTML().html {
    head {
      title("Indexing diagnostics of '$projectName'")
      style {
        unsafe {
          +CSS_STYLE
        }
      }
      script {
        unsafe {
          +LINKABLE_TABLE_ROW_SCRIPT
        }
      }
    }
    body {
      div(classes = "aggregate-activity-report-content") {
        h1(classes = "aggregate-header") { text(projectName) }

        div {
          text("All durations are wall time; total and content loading time include pauses")
          table(classes = "centered-text table-with-margin activity-table") {
            unsafe {
              +"<caption style=\"caption-side: bottom; text-align: right; font-size: 14px\">Click for details</caption>"
            }
            thead {
              tr {
                th("History of scannings and indexings") { colSpan = "14" }
              }
              tr {
                th("Time") {
                  colSpan = "7"
                }
                th("Files") {
                  colSpan = "5"
                }
                th("Scanning ID(s)") {
                  rowSpan = "2"
                }
                th("Scanning Type") {
                  rowSpan = "2"
                }
              }
              tr {
                th("Started")
                th("Started dumb mode")
                th("Finished")
                th { text("Total"); unsafe { raw("&nbsp;") }; text("time") }
                th("Time spent on pause")
                th("Full dumb mode time")
                th("Dumb mode time w/o pauses")
                th("Scanned")
                th("Shared indexes (w/o content loading)")
                th("Scheduled for indexing")
                th("Shared indexes (content loaded)")
                th("Total indexed (shared indexes included)")
              }
            }
            tbody {
              for (diagnostic in diagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }) {
                val times = diagnostic.indexingTimes

                val classes = if (times is JsonProjectScanningHistoryTimes) {
                  "linkable-table-row scanning-table-row"
                }
                else {
                  "linkable-table-row"
                }
                tr(classes = classes) {
                  attributes["href"] = diagnostic.htmlFile.fileName.toString()
                  printIndexingActivityRow(times, diagnostic.fileCount)
                }
              }
            }
          }
        }

        if (sharedIndexEvents.isNotEmpty()) {
          val indexIdToEvents = sharedIndexEvents.groupBy { it.chunkUniqueId }
          div {
            table(classes = "table-with-margin activity-table") {
              thead {
                tr {
                  th("Shared indexes") {
                    colSpan = "9"
                  }
                }
                tr {
                  th("Time")
                  th("Kind")
                  th("Name")
                  th("Size")
                  th("Download time")
                  th("Download speed")
                  th("Status")
                  th("ID")
                  th("Generation time")
                }
              }
              tbody {
                for (event in sharedIndexEvents.filterIsInstance<JsonSharedIndexDiagnosticEvent.Downloaded>().sortedByDescending { it.time.instant }) {
                  val events = indexIdToEvents.getOrDefault(event.chunkUniqueId, emptyList())
                  val lastAttach = events.filterIsInstance<JsonSharedIndexDiagnosticEvent.Attached>().maxByOrNull { it.time.instant }
                                   ?: continue
                  tr {
                    td(event.time.presentableLocalDateTime())
                    td(lastAttach.kind)
                    td((lastAttach as? JsonSharedIndexDiagnosticEvent.Attached.Success)?.indexName ?: NOT_APPLICABLE)
                    td(event.packedSize.presentableSize())
                    td(event.downloadTime.presentableDuration())
                    td(event.downloadSpeed.presentableSpeed())
                    td(event.finishType + ((lastAttach as? JsonSharedIndexDiagnosticEvent.Attached.Success)?.let {
                      " FB: ${it.fbMatch.presentablePercentages()}, Stub: ${it.stubMatch.presentablePercentages()}"
                    } ?: " Incompatible"))
                    td(event.chunkUniqueId)
                    td(event.generationTime?.presentableLocalDateTime() ?: "unknown")
                  }
                }
              }
            }
          }
        }

        if (changedFilesPushEvents.isNotEmpty()) {
          div {
            table(classes = "table-with-margin activity-table") {
              thead {
                tr {
                  th("Scanning to push properties of changed files") {
                    colSpan = "5"
                  }
                }
                tr {
                  th("Time")
                  th("Reason")
                  th("Full duration")
                  th("Is cancelled")
                  th("Number")
                }
              }
              tbody {
                val eventsToUnify = mutableListOf<ChangedFilesPushedEvent>()
                for (event in changedFilesPushEvents.sortedByDescending { it.startTime.instant }) {
                  if (canUnify(event, eventsToUnify)) {
                    eventsToUnify.add(event)
                  }
                  else {
                    printUnified(eventsToUnify)
                    eventsToUnify.clear()
                    printEvent(event)
                  }
                }
                printUnified(eventsToUnify)
              }
            }
          }
        }
      }
    }
  }
}

private fun TR.printIndexingActivityRow(times: JsonProjectIndexingActivityHistoryTimes,
                                        fileCount: JsonProjectIndexingActivityFileCount) {
  // Time section
  td(times.updatingStart.presentableLocalDateTime())

  td(times.dumbModeStart?.presentableLocalDateTime() ?: DID_NOT_START)

  td {
    if (times.wasInterrupted) {
      strong("Cancelled")
      br()
    }
    text(times.updatingEnd.presentableLocalDateTime())
  }

  td(times.totalWallTimeWithPauses.presentableDuration())
  td(times.wallTimeOnPause.presentableDuration())

  td(times.dumbWallTimeWithPauses.presentableDuration())
  td(times.dumbWallTimeWithoutPauses.presentableDuration())

  // Files section.
  when (fileCount) {
    is JsonProjectScanningFileCount -> {
      td(fileCount.numberOfScannedFiles.toString())
      td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan.toString())
      td(fileCount.numberOfFilesScheduledForIndexingAfterScan.toString())
      td(NOT_APPLICABLE)
      td(NOT_APPLICABLE)
    }
    is JsonProjectDumbIndexingFileCount -> {
      if (IndexDiagnosticDumper.shouldPrintInformationAboutChangedDuringIndexingActionFilesInAggregateHtml) {
        td(fileCount.numberOfChangedDuringIndexingFiles.toString())
        td(NOT_APPLICABLE)
        td(fileCount.numberOfChangedDuringIndexingFiles.toString())
      }
      else {
        td(NOT_APPLICABLE)
        td(NOT_APPLICABLE)
        td(NOT_APPLICABLE)
      }
      td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage.toString())
      if (fileCount.numberOfChangedDuringIndexingFiles > 0) {
        td {
          text(fileCount.numberOfFilesIndexedWithLoadingContent.toString())
          br()
          text("(incl. ${fileCount.numberOfChangedDuringIndexingFiles} changed during indexing)")
        }
      }
      else {
        td(fileCount.numberOfFilesIndexedWithLoadingContent.toString())
      }
    }
  }

  //Scanning ID(s) section
  td(times.asSafely<JsonProjectScanningHistoryTimes>()?.scanningId?.toString()
     ?: times.asSafely<JsonProjectDumbIndexingHistoryTimes>()?.scanningIds?.let {
       if (it.isEmpty()) "None"
       else it.toString().trimStart('[').trimEnd(']')
     }
     ?: "Unexpected times $times")

  //Scanning type section
  td(times.asSafely<JsonProjectScanningHistoryTimes>()?.scanningType?.name?.lowercase(Locale.ENGLISH)?.replace('_', ' ')
     ?: NOT_APPLICABLE)
}

private fun TBODY.printEvent(event: ChangedFilesPushedEvent) {
  tr {
    td(event.startTime.presentableLocalDateTime())
    td(event.reason)
    td(event.duration.presentableDuration())
    td(if (event.isCancelled) "cancelled" else "fully finished")
    td("1")
  }
}

private fun TBODY.printUnified(eventsToUnify: List<ChangedFilesPushedEvent>) {
  if (eventsToUnify.isEmpty()) return
  val event = eventsToUnify[0]
  if (eventsToUnify.size == 1) {
    printEvent(event)
    return
  }
  tr {
    td(event.startTime.presentableLocalDateTime())
    td(event.reason)
    td(JsonDuration(eventsToUnify.sumOf { it.duration.nano }).presentableDuration())
    td(if (event.isCancelled) "cancelled" else "fully finished")
    td(eventsToUnify.size.toString())
  }
}

private fun canUnify(event: ChangedFilesPushedEvent, baseList: List<ChangedFilesPushedEvent>): Boolean {
  if (event.isCancelled || event.duration.nano > 1_000_000) {
    return false
  }
  if (baseList.isEmpty()) {
    return true
  }
  val first = baseList[0]
  return first.reason == event.reason
}

private const val NOT_APPLICABLE = "N/A"
private const val DID_NOT_START = "Didn't start"

private const val SECTION_PROJECT_NAME_ID = "id-project-name"
private const val SECTION_PROJECT_NAME_TITLE = "Project name"

private const val SECTION_APP_INFO_ID = "id-app-info"
private const val SECTION_APP_INFO_TITLE = "Application info"

private const val SECTION_RUNTIME_INFO_ID = "id-runtime-info"
private const val SECTION_RUNTIME_INFO_TITLE = "Runtime"

private const val SECTION_INDEXING_INFO_ID = "id-indexing-info"
private const val SECTION_INDEXING_INFO_TITLE = "Indexing info"
private const val SECTION_OVERVIEW_TITLE = "Overview"

private const val SECTION_SLOW_FILES_ID = "id-slow-files"
private const val SECTION_SLOW_FILES_TITLE = "Slowly indexed files"

private const val SECTION_STATS_PER_FILE_TYPE_ID = "id-stats-per-file-type"
private const val SECTION_STATS_PER_FILE_TYPE_TITLE = "Statistics per file type"

private const val SECTION_STATS_PER_INDEXER_ID = "id-stats-per-indexer"
private const val SECTION_STATS_PER_INDEXER_TITLE = "Statistics per indexer"

private const val SECTION_SCANNING_ID = "id-scanning"
private const val SECTION_SCANNING_TITLE = "Scanning"
private const val SECTION_SCANNING_PER_PROVIDER_TITLE = "Scanning per provider"

private const val SECTION_INDEXING_ID = "id-indexing"
private const val SECTION_INDEXING_TITLE = "Indexing"

/**
 * For now, we have only Shared Indexes implementation of FileBasedIndexInfrastructureExtension,
 * so for simplicity let's use this name instead of a general "index infrastructure extensions".
 */
private const val INDEX_INFRA_EXTENSIONS = "shared indexes"

private const val TITLE_NUMBER_OF_FILE_PROVIDERS = "Number of file providers"
private const val TITLE_NUMBER_OF_SCANNED_FILES = "Number of scanned files"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRA_EXTENSIONS_DURING_SCAN = "Number of files indexed by $INDEX_INFRA_EXTENSIONS during the scan (without loading content)"
private const val TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN = "Number of files scheduled for indexing after scanning"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING = "Number of files indexed by $INDEX_INFRA_EXTENSIONS during the indexing stage (with loading content)"
private const val TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT = "Number of files indexed during the indexing stage with loading content (including indexed by $INDEX_INFRA_EXTENSIONS)"

private fun FlowContent.printRuntimeInfo(runtimeInfo: JsonRuntimeInfo) {
  div(id = SECTION_RUNTIME_INFO_ID) {
    h1(SECTION_RUNTIME_INFO_TITLE)
    table(classes = "two-columns") {
      thead {
        tr { th("Name"); th("Value") }
      }
      tbody {
        tr { td("Max memory"); td(StringUtil.formatFileSize(runtimeInfo.maxMemory)) }
        tr { td("Number of processors"); td(runtimeInfo.numberOfProcessors.toString()) }
        tr { td("Max number of indexing threads"); td(runtimeInfo.maxNumberOfIndexingThreads.toString()) }
        tr { td("Max size of file for analysis"); td(StringUtil.formatFileSize(runtimeInfo.maxSizeOfFileForIntelliSense.toLong())) }
        tr {
          td("Max size of file for content loading"); td(StringUtil.formatFileSize(runtimeInfo.maxSizeOfFileForContentLoading.toLong()))
        }
      }
    }
  }
}

private fun FlowContent.printRuntimeInfoForActivity(runtimeInfo: JsonRuntimeInfo) {
  div(id = SECTION_RUNTIME_INFO_ID) {
    table(classes = "two-columns table-with-margin narrow-activity-table") {
      thead {
        tr { th(SECTION_RUNTIME_INFO_TITLE) { colSpan = "2" } }
      }
      tbody {
        tr { td("Max memory"); td(StringUtil.formatFileSize(runtimeInfo.maxMemory)) }
        tr { td("Number of processors"); td(runtimeInfo.numberOfProcessors.toString()) }
        tr { td("Max number of indexing threads"); td(runtimeInfo.maxNumberOfIndexingThreads.toString()) }
        tr { td("Max size of file for analysis"); td(StringUtil.formatFileSize(runtimeInfo.maxSizeOfFileForIntelliSense.toLong())) }
        tr {
          td("Max size of file for content loading"); td(StringUtil.formatFileSize(runtimeInfo.maxSizeOfFileForContentLoading.toLong()))
        }
      }
    }
  }
}

private fun FlowContent.printAppInfo(appInfo: JsonIndexDiagnosticAppInfo) {
  div(id = SECTION_APP_INFO_ID) {
    h1(SECTION_APP_INFO_TITLE)
    table(classes = "two-columns") {
      thead {
        tr { th("Name"); th("Value") }
      }
      tbody {
        tr { td("Build"); td(appInfo.build) }
        tr { td("Build date"); td(appInfo.buildDate.presentableLocalDateTime()) }
        tr { td("Product code"); td(appInfo.productCode) }
        tr { td("Generated"); td(appInfo.generated.presentableLocalDateTime()) }
        tr { td("OS"); td(appInfo.os) }
        tr { td("Runtime"); td(appInfo.runtime) }
      }
    }
  }
}

private fun FlowContent.printAppInfoForActivity(appInfo: JsonIndexDiagnosticAppInfo) {
  div(id = SECTION_APP_INFO_ID) {
    table(classes = "two-columns table-with-margin narrow-activity-table") {
      thead {
        tr { th(SECTION_APP_INFO_TITLE) { colSpan = "2" } }
      }
      tbody {
        tr { td("Build"); td(appInfo.build) }
        tr { td("Build date"); td(appInfo.buildDate.presentableLocalDateTime()) }
        tr { td("Product code"); td(appInfo.productCode) }
        tr { td("Generated"); td(appInfo.generated.presentableLocalDateTime()) }
        tr { td("OS"); td(appInfo.os) }
        tr { td("Runtime"); td(appInfo.runtime) }
      }
    }
  }
}

private const val HIDE_MINOR_DATA_INITIAL = true
private fun getMinorDataClass(isMinor: Boolean) = if (isMinor) "minor-data" + (if (HIDE_MINOR_DATA_INITIAL) " invisible" else "") else ""

fun JsonIndexDiagnostic.generateHtml(target: Appendable): String {
  return target.appendHTML().html {
    head {
      title("Indexing diagnostics of '${projectIndexingHistory.projectName}'")
      style {
        unsafe {
          +CSS_STYLE
        }
      }
      script {
        unsafe {
          +JS_SCRIPT
        }
      }
    }
    body {
      div(classes = "navigation-bar") {
        ul {
          li {
            a("#$SECTION_PROJECT_NAME_ID") {
              text(SECTION_PROJECT_NAME_TITLE)
            }
          }
          li {
            a("#$SECTION_APP_INFO_ID") {
              text(SECTION_APP_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_RUNTIME_INFO_ID") {
              text(SECTION_RUNTIME_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_INDEXING_INFO_ID") {
              text(SECTION_INDEXING_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_SLOW_FILES_ID") {
              text(SECTION_SLOW_FILES_TITLE)
            }
          }
          li {
            a("#$SECTION_STATS_PER_FILE_TYPE_ID") {
              text(SECTION_STATS_PER_FILE_TYPE_TITLE)
            }
          }
          li {
            a("#$SECTION_STATS_PER_INDEXER_ID") {
              text(SECTION_STATS_PER_INDEXER_TITLE)
            }
          }
          li {
            a("#$SECTION_SCANNING_ID") {
              text(SECTION_SCANNING_TITLE)
            }
          }
          li {
            a("#$SECTION_INDEXING_ID") {
              text(SECTION_INDEXING_TITLE)
            }
          }
        }
        hr("solid")
        ul {
          li {
            label {
              attributes["for"] = "id-hide-minor-data-checkbox"
              text("Hide minor data")
              input {
                checked = HIDE_MINOR_DATA_INITIAL
                id = "id-hide-minor-data-checkbox"
                type = InputType.checkBox
                onClick = "hideElementsHavingClass('minor-data', this.checked)"
                style {
                  unsafe {
                    +"padding-left: 10px"
                  }
                }
              }
            }
          }
        }
        div(classes = "jetbrains-logo") {
          unsafe {
            +JETBRAINS_GRAYSCALE_LOGO_SVG
          }
        }
      }

      div(classes = "stats-content") {
        div(id = SECTION_PROJECT_NAME_ID) {
          h1(SECTION_PROJECT_NAME_TITLE)
          text(projectIndexingHistory.projectName)
        }

        printAppInfo(appInfo)
        printRuntimeInfo(runtimeInfo)

        div(id = SECTION_INDEXING_INFO_ID) {
          h1(SECTION_INDEXING_INFO_TITLE)
          table(classes = "two-columns") {
            thead {
              tr { th("Name"); th("Data") }
            }
            tbody {
              val fileCount = projectIndexingHistory.fileCount
              tr { td(TITLE_NUMBER_OF_FILE_PROVIDERS); td(fileCount.numberOfFileProviders.toString()) }
              tr { td(TITLE_NUMBER_OF_SCANNED_FILES); td(fileCount.numberOfScannedFiles.toString()) }
              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRA_EXTENSIONS_DURING_SCAN)
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN)
                td(fileCount.numberOfFilesScheduledForIndexingAfterScan.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING)
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT)
                td(fileCount.numberOfFilesIndexedWithLoadingContent.toString())
              }
              tr {
                td("Number of too large for indexing files")
                td(projectIndexingHistory.fileProviderStatistics.sumOf { it.numberOfTooLargeForIndexingFiles }.toString())
              }

              val times = projectIndexingHistory.times
              tr { td("Started at"); td(times.updatingStart.presentableLocalDateTime()) }
              if (times.indexingReason != null) {
                tr { td("Reason"); td(times.indexingReason) }
              }
              tr { td("Type"); td(times.scanningType.name.lowercase(Locale.ENGLISH).replace('_', ' ')) }
              tr { td("Finished at"); td(times.updatingEnd.presentableLocalDateTime()) }
              tr { td("Cancelled?"); td(times.wasInterrupted.toString()) }
              tr { td("Suspended time"); td(times.totalSuspendedTime.presentableDuration()) }
              tr { td("Total time"); td(times.totalUpdatingTime.presentableDuration()) }
              tr { td("Indexing time"); td(times.indexingTime.presentableDuration()) }
              tr { td("Iterators creation time"); td(times.creatingIteratorsTime.presentableDuration()) }
              if (IndexDiagnosticDumper.shouldProvideVisibleAndAllThreadsTimeInfo) {
                tr {
                  td("Total processing visible time")
                  td(JsonDuration(
                    projectIndexingHistory.fileProviderStatistics.sumOf { stat -> stat.totalIndexingVisibleTime.nano }).presentableDuration())
                }
                tr {
                  td("All threads time to visible time ratio")
                  td(String.format("%.2f", projectIndexingHistory.visibleTimeToAllThreadTimeRatio))
                }
              }
              tr { td("Scanning time"); td(times.scanFilesTime.presentableDuration()) }
              tr { td("Content loading time"); td(times.contentLoadingVisibleTime.presentableDuration()) }
              tr { td("Pushing properties time"); td(times.pushPropertiesTime.presentableDuration()) }
              tr { td("Running extensions time"); td(times.indexExtensionsTime.presentableDuration()) }
              tr {
                td("Index writing time")
                td(if (times.isAppliedAllValuesSeparately)
                     StringUtil.formatDuration(times.separateApplyingIndexesVisibleTime.milliseconds)
                   else
                     "Applied under read lock"
                )
              }
            }
          }
        }

        div(id = SECTION_SLOW_FILES_ID) {
          h1("$SECTION_SLOW_FILES_TITLE (> ${IndexingFileSetStatistics.SLOW_FILE_PROCESSING_THRESHOLD_MS} ms)")
          table {
            thead {
              tr {
                th("Provider name")
                th("File")
                th("Content loading time")
                th("Indexing time")
                th("Total processing time")
              }
            }
            tbody {
              for (providerStatistic in projectIndexingHistory.fileProviderStatistics.filter { it.slowIndexedFiles.isNotEmpty() }) {
                val slowIndexedFiles = providerStatistic.slowIndexedFiles
                for ((index, slowFile) in slowIndexedFiles.sortedByDescending { it.processingTime.nano }.withIndex()) {
                  tr {
                    td(if (index == 0) providerStatistic.providerName else "^")
                    td(slowFile.fileName)
                    td(slowFile.contentLoadingTime.presentableDuration())
                    td(slowFile.evaluationOfIndexValueChangerTime.presentableDuration())
                    td(slowFile.processingTime.presentableDuration())
                  }
                }
              }
            }
          }
        }

        div(id = SECTION_STATS_PER_FILE_TYPE_ID) {
          h1(SECTION_STATS_PER_FILE_TYPE_TITLE)
          table {
            thead {
              tr {
                th("File type")
                th("Number of files")
                th("Total processing time")
                th("Content loading time")
                th("Total files size")
                th("Total processing speed")
                th("The biggest contributors")
              }
            }
            tbody {
              for (statsPerFileType in projectIndexingHistory.totalStatsPerFileType) {
                val visibleIndexingTime = JsonDuration(
                  (projectIndexingHistory.times.indexingTime.nano * statsPerFileType.partOfTotalProcessingTime.partition).toLong()
                )
                val visibleContentLoadingTime = JsonDuration(
                  (projectIndexingHistory.times.contentLoadingVisibleTime.nano * statsPerFileType.partOfTotalContentLoadingTime.partition).toLong()
                )
                tr(classes = getMinorDataClass(visibleIndexingTime.milliseconds < 500)) {
                  td(statsPerFileType.fileType)
                  td(statsPerFileType.totalNumberOfFiles.toString())
                  td(
                    visibleIndexingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalProcessingTime.presentablePercentages() + ")")
                  td(
                    visibleContentLoadingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalContentLoadingTime.presentablePercentages() + ")")
                  td(statsPerFileType.totalFilesSize.presentableSize())
                  td(statsPerFileType.totalProcessingSpeed.presentableSpeed())
                  td(
                    statsPerFileType.biggestContributors.joinToString("\n") {
                      it.partOfTotalProcessingTimeOfThisFileType.presentablePercentages() + ": " +
                      it.providerName + " " +
                      it.numberOfFiles + " files of total size " +
                      it.totalFilesSize.presentableSize()
                    }
                  )
                }
              }
            }
          }
        }

        div(id = SECTION_STATS_PER_INDEXER_ID) {
          h1(SECTION_STATS_PER_INDEXER_TITLE)
          table {
            thead {
              tr {
                th("Index")
                th("Number of files")
                th("Part of total indexing time")
                th("Total number of files indexed by $INDEX_INFRA_EXTENSIONS")
                th("Total files size")
                th("Indexing speed")
                th("Snapshot input mapping statistics")
              }
            }
            tbody {
              for (statsPerIndexer in projectIndexingHistory.totalStatsPerIndexer) {
                tr(classes = getMinorDataClass(statsPerIndexer.partOfTotalIndexingTime.partition < 0.1)) {
                  td(statsPerIndexer.indexId)
                  td(statsPerIndexer.totalNumberOfFiles.toString())
                  td(statsPerIndexer.partOfTotalIndexingTime.presentablePercentages())
                  td(statsPerIndexer.totalNumberOfFilesIndexedByExtensions.toString())
                  td(statsPerIndexer.totalFilesSize.presentableSize())
                  td(statsPerIndexer.indexValueChangerEvaluationSpeed.presentableSpeed())

                  fun JsonProjectIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats.presentable(): String {
                    val hitsPercentages = JsonPercentages(totalHits, totalRequests)
                    val missesPercentages = JsonPercentages(totalMisses, totalRequests)
                    return "requests: $totalRequests, " +
                           "hits: $totalHits (${hitsPercentages.presentablePercentages()}), " +
                           "misses: $totalMisses (${missesPercentages.presentablePercentages()})"
                  }
                  td(statsPerIndexer.snapshotInputMappingStats.presentable())
                }
              }
            }
          }
        }

        val shouldPrintScannedFiles = projectIndexingHistory.scanningStatistics.any { it.scannedFiles.orEmpty().isNotEmpty() }
        val shouldPrintProviderRoots = projectIndexingHistory.scanningStatistics.any { it.roots.isNotEmpty() }
        div(id = SECTION_SCANNING_ID) {
          h1(SECTION_SCANNING_TITLE)
          table {
            thead {
              tr {
                th("Provider name")
                th("Number of scanned files")
                th("Number of files scheduled for indexing")
                th("Number of files fully indexed by $INDEX_INFRA_EXTENSIONS")
                th("Number of double-scanned skipped files")
                th("Total time of getting files' statuses (part of scanning)")
                th("Scanning time")
                th("Time processing up-to-date files")
                th("Time updating content-less indexes")
                th("Time indexing without content")
                if (shouldPrintProviderRoots) {
                  th("Roots")
                }
                if (shouldPrintScannedFiles) {
                  th("Scanned files")
                }
              }
            }
            tbody {
              for (scanningStats in projectIndexingHistory.scanningStatistics) {
                tr(classes = getMinorDataClass(
                  scanningStats.scanningTime.milliseconds < 100 && scanningStats.numberOfScannedFiles < 1000)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.statusTime.presentableDuration())
                  td(scanningStats.scanningTime.presentableDuration())
                  td(scanningStats.timeProcessingUpToDateFiles.presentableDuration())
                  td(scanningStats.timeUpdatingContentLessIndexes.presentableDuration())
                  td(scanningStats.timeIndexingWithoutContentViaInfrastructureExtension.presentableDuration())
                  if (shouldPrintProviderRoots) {
                    td {
                      textArea(scanningStats.roots.sorted().joinToString("\n"))
                    }
                  }
                  if (shouldPrintScannedFiles) {
                    td {
                      textArea(
                        scanningStats.scannedFiles.orEmpty().joinToString("\n") { file ->
                          file.path.presentablePath + when {
                            file.wasFullyIndexedByInfrastructureExtension -> " [by infrastructure]"
                            file.isUpToDate -> " [up-to-date]"
                            else -> ""
                          }
                        }
                      )
                    }
                  }
                }
              }
            }
          }
        }

        val shouldPrintIndexedFiles = projectIndexingHistory.fileProviderStatistics.any { it.indexedFiles.orEmpty().isNotEmpty() }
        div(id = SECTION_INDEXING_ID) {
          h1(SECTION_INDEXING_TITLE)
          table {
            thead {
              tr {
                th("Provider name")
                th("Total processing time")
                th("Content loading time")
                th("Number of indexed files")
                th("Number of files indexed by $INDEX_INFRA_EXTENSIONS")
                th("Number of too large for indexing files")
                if (shouldPrintIndexedFiles) {
                  th("Indexed files")
                }
              }
            }
            tbody {
              for (providerStats in projectIndexingHistory.fileProviderStatistics) {
                tr(classes = getMinorDataClass(
                  providerStats.totalIndexingVisibleTime.milliseconds < 100 && providerStats.totalNumberOfIndexedFiles < 1000)) {
                  td(providerStats.providerName)
                  td(providerStats.totalIndexingVisibleTime.presentableDuration())
                  td(providerStats.contentLoadingVisibleTime.presentableDuration())
                  td(providerStats.totalNumberOfIndexedFiles.toString())
                  td(providerStats.totalNumberOfFilesFullyIndexedByExtensions.toString())
                  td(providerStats.numberOfTooLargeForIndexingFiles.toString())
                  if (shouldPrintIndexedFiles) {
                    td {
                      textArea(
                        providerStats.indexedFiles.orEmpty().joinToString("\n") { file ->
                          file.path.presentablePath + if (file.wasFullyIndexedByExtensions) " [by infrastructure]" else ""
                        }
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }.toString()
}

fun JsonIndexingActivityDiagnostic.generateHtml(target: Appendable): String =
  when (type) {
    IndexDiagnosticDumper.IndexingActivityType.Scanning ->
      (this.projectIndexingActivityHistory as JsonProjectScanningHistory).generateScanningHtml(target, appInfo, runtimeInfo)
    IndexDiagnosticDumper.IndexingActivityType.DumbIndexing ->
      (this.projectIndexingActivityHistory as JsonProjectDumbIndexingHistory).generateDumbIndexingHtml(target, appInfo, runtimeInfo)
  }


private fun JsonProjectScanningHistory.generateScanningHtml(target: Appendable,
                                                            appInfo: JsonIndexDiagnosticAppInfo,
                                                            runtimeInfo: JsonRuntimeInfo): String {
  return target.appendHTML().html {
    head {
      title("Indexing diagnostics of '${projectName}'")
      style {
        unsafe {
          +CSS_STYLE
        }
      }
      script {
        unsafe {
          +JS_SCRIPT
        }
      }
    }
    body {
      div(classes = "navigation-bar") {
        ul {
          li {
            a("#$SECTION_PROJECT_NAME_ID") {
              text(SECTION_PROJECT_NAME_TITLE)
            }
          }
          li {
            a("#$SECTION_APP_INFO_ID") {
              text(SECTION_APP_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_RUNTIME_INFO_ID") {
              text(SECTION_RUNTIME_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_INDEXING_INFO_ID") {
              text(SECTION_OVERVIEW_TITLE)
            }
          }
          li {
            a("#$SECTION_SCANNING_ID") {
              text(SECTION_SCANNING_PER_PROVIDER_TITLE)
            }
          }
        }
        hr("solid")
        ul {
          li {
            label {
              attributes["for"] = "id-hide-minor-data-checkbox"
              text("Hide minor data")
              input {
                checked = HIDE_MINOR_DATA_INITIAL
                id = "id-hide-minor-data-checkbox"
                type = InputType.checkBox
                onClick = "hideElementsHavingClass('minor-data', this.checked)"
                style {
                  unsafe {
                    +"padding-left: 10px"
                  }
                }
              }
            }
          }
        }
        div(classes = "jetbrains-logo") {
          unsafe {
            +JETBRAINS_GRAYSCALE_LOGO_SVG
          }
        }
      }

      div(classes = "stats-activity-content") {
        printProjectNameForActivity(projectName)
        printDurationDescription()
        printAppInfoForActivity(appInfo)
        printRuntimeInfoForActivity(runtimeInfo)

        div(id = SECTION_INDEXING_INFO_ID) {
          table(classes = "two-columns table-with-margin narrow-activity-table") {
            thead {
              tr { th(SECTION_OVERVIEW_TITLE) { colSpan = "2" } }
            }
            tbody {
              tr {
                td("Activity"); td("Scanning")
              }
              tr {
                td("Scanning session ID"); td(times.scanningId.toString())
              }

              tr { td("Started at"); td(times.updatingStart.presentableLocalDateTimeWithMilliseconds()) }
              if (times.scanningReason != null) {
                tr { td("Reason"); td(times.scanningReason) }
              }
              tr { td("Type"); td(times.scanningType.name.lowercase(Locale.ENGLISH).replace('_', ' ')) }
              tr { td("Dumb mode start"); td(times.dumbModeStart?.presentableLocalDateTimeWithMilliseconds() ?: "Didn't happen") }

              tr { td("Finished at"); td(times.updatingEnd.presentableLocalDateTimeWithMilliseconds()) }
              tr { td("Cancelled"); td(times.wasInterrupted.toString()) }
              tr { td("Total time with pauses"); td(times.totalWallTimeWithPauses.presentableDuration()) }
              tr { td("Time spent on pause"); td(times.wallTimeOnPause.presentableDuration()) }
              tr { td("Total time w/o pauses"); td(times.totalWallTimeWithoutPauses.presentableDuration()) }
              tr { td("Dumb mode time with pauses"); td(times.dumbWallTimeWithPauses.presentableDuration()) }
              tr { td("Dumb mode time w/o pauses"); td(times.dumbWallTimeWithoutPauses.presentableDuration()) }
              tr {
                td("Time of collecting files to compute index values (w/o pauses)")
                td(times.collectingIndexableFilesTime.presentableDuration())
              }
              tr {
                td("Time of running $INDEX_INFRA_EXTENSIONS (without loading content; sum of durations on many threads)"); td(times.indexExtensionsTime.presentableDuration())
              }

              tr { td(TITLE_NUMBER_OF_FILE_PROVIDERS); td(fileCount.numberOfFileProviders.toString()) }
              tr { td(TITLE_NUMBER_OF_SCANNED_FILES); td(fileCount.numberOfScannedFiles.toString()) }
              tr {
                td("Number of files indexed by $INDEX_INFRA_EXTENSIONS (without loading content)")
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN)
                td(fileCount.numberOfFilesScheduledForIndexingAfterScan.toString())
              }
            }
          }
        }

        val shouldPrintScannedFiles = scanningStatistics.any { it.scannedFiles.orEmpty().isNotEmpty() }
        val shouldPrintProviderRoots = scanningStatistics.any { it.roots.isNotEmpty() }
        div(id = SECTION_SCANNING_ID) {
          h1 { text(SECTION_SCANNING_PER_PROVIDER_TITLE) }
          text("Total time consists of pushing and getting files' statuses. " +
               "Processing up-to-date files, updating content-less indexes, indexing by $INDEX_INFRA_EXTENSIONS without content " +
               "happens during getting files' statuses.")
          table(classes = "table-with-margin activity-table") {
            thead {
              tr {
                th("Provider name")
                th("Number of scanned files")
                th("Number of files scheduled for indexing")
                th("Number of files fully indexed by $INDEX_INFRA_EXTENSIONS")
                th("Number of double-scanned skipped files")
                th { text("Total"); unsafe { raw("&nbsp;") }; text("time") }
                th("Time of getting files' statuses")
                th("Time processing up-to-date files")
                th("Time updating content-less indexes")
                th("Time indexing by $INDEX_INFRA_EXTENSIONS without content")
                if (shouldPrintProviderRoots) {
                  th("Roots")
                }
                if (shouldPrintScannedFiles) {
                  th("Scanned files")
                }
              }
            }
            tbody {
              for (scanningStats in scanningStatistics) {
                tr(classes = getMinorDataClass(
                  scanningStats.scanningTime.milliseconds < 100 && scanningStats.numberOfScannedFiles < 1000)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.scanningTime.presentableDuration())
                  td(scanningStats.statusTime.presentableDuration())
                  td(scanningStats.timeProcessingUpToDateFiles.presentableDuration())
                  td(scanningStats.timeUpdatingContentLessIndexes.presentableDuration())
                  td(scanningStats.timeIndexingWithoutContentViaInfrastructureExtension.presentableDuration())
                  if (shouldPrintProviderRoots) {
                    td {
                      textArea(scanningStats.roots.sorted().joinToString("\n"))
                    }
                  }
                  if (shouldPrintScannedFiles) {
                    td {
                      textArea(
                        scanningStats.scannedFiles.orEmpty().joinToString("\n") { file ->
                          file.path.presentablePath + when {
                            file.wasFullyIndexedByInfrastructureExtension -> " [by infrastructure]"
                            file.isUpToDate -> " [up-to-date]"
                            else -> ""
                          }
                        }
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }.toString()
}

private fun DIV.printDurationDescription() {
  text("All durations are wall time; they include pauses unless specified otherwise")
}

private fun JsonProjectDumbIndexingHistory.generateDumbIndexingHtml(target: Appendable,
                                                                    appInfo: JsonIndexDiagnosticAppInfo,
                                                                    runtimeInfo: JsonRuntimeInfo): String {
  return target.appendHTML().html {
    head {
      title("Indexing diagnostics of '${projectName}'")
      style {
        unsafe {
          +CSS_STYLE
        }
      }
      script {
        unsafe {
          +JS_SCRIPT
        }
      }
    }
    body {
      div(classes = "navigation-bar") {
        ul {
          li {
            a("#$SECTION_PROJECT_NAME_ID") {
              text(SECTION_PROJECT_NAME_TITLE)
            }
          }
          li {
            a("#$SECTION_APP_INFO_ID") {
              text(SECTION_APP_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_RUNTIME_INFO_ID") {
              text(SECTION_RUNTIME_INFO_TITLE)
            }
          }
          li {
            a("#$SECTION_INDEXING_INFO_ID") {
              text(SECTION_OVERVIEW_TITLE)
            }
          }
          li {
            a("#$SECTION_SLOW_FILES_ID") {
              text(SECTION_SLOW_FILES_TITLE)
            }
          }
          li {
            a("#$SECTION_STATS_PER_FILE_TYPE_ID") {
              text(SECTION_STATS_PER_FILE_TYPE_TITLE)
            }
          }
          li {
            a("#$SECTION_STATS_PER_INDEXER_ID") {
              text(SECTION_STATS_PER_INDEXER_TITLE)
            }
          }
          li {
            a("#$SECTION_INDEXING_ID") {
              text(SECTION_INDEXING_TITLE)
            }
          }
        }
        hr("solid")
        ul {
          li {
            label {
              attributes["for"] = "id-hide-minor-data-checkbox"
              text("Hide minor data")
              input {
                checked = HIDE_MINOR_DATA_INITIAL
                id = "id-hide-minor-data-checkbox"
                type = InputType.checkBox
                onClick = "hideElementsHavingClass('minor-data', this.checked)"
                style {
                  unsafe {
                    +"padding-left: 10px"
                  }
                }
              }
            }
          }
        }
        div(classes = "jetbrains-logo") {
          unsafe {
            +JETBRAINS_GRAYSCALE_LOGO_SVG
          }
        }
      }

      div(classes = "stats-activity-content") {
        printProjectNameForActivity(projectName)
        printDurationDescription()
        printAppInfoForActivity(appInfo)
        printRuntimeInfoForActivity(runtimeInfo)

        div(id = SECTION_INDEXING_INFO_ID) {
          table(classes = "two-columns table-with-margin narrow-activity-table") {
            thead {
              tr { th(SECTION_OVERVIEW_TITLE) { colSpan = "2" } }
            }
            tbody {
              tr {
                td("Activity"); td("Dumb indexing")
              }

              tr {
                td("Indexed files from scanning sessions with IDs")
                td(times.scanningIds.let { ids ->
                  if (ids.isEmpty()) "Only refreshed files were indexed"
                  else ids.toString().trimStart('[').trimEnd(']').plus(" (and refreshed files)")
                })
              }

              val times = times
              tr { td("Started at"); td(times.updatingStart.presentableLocalDateTime()) }
              tr { td("Finished at"); td(times.updatingEnd.presentableLocalDateTime()) }
              tr { td("Cancelled?"); td(times.wasInterrupted.toString()) }
              tr { td("Total time with pauses"); td(times.totalWallTimeWithPauses.presentableDuration()) }
              tr { td("Pauses time"); td(times.wallTimeOnPause.presentableDuration()) }
              if (IndexDiagnosticDumper.shouldProvideVisibleAndAllThreadsTimeInfo) {
                tr {
                  td("Total processing visible time")
                  td(JsonDuration(fileProviderStatistics.sumOf { stat -> stat.totalIndexingVisibleTime.nano }).presentableDuration())
                }
                tr {
                  td("All threads time to visible time ratio")
                  td(String.format("%.2f", visibleTimeToAllThreadTimeRatio))
                }
              }
              tr { td("Time of retrieving files changed during indexing"); td(times.retrievingChangedDuringIndexingFilesTime.presentableDuration()) }
              tr { td("Content loading time"); td(times.contentLoadingVisibleTime.presentableDuration()) }
              tr {
                td("Index writing time")
                td(if (times.isAppliedAllValuesSeparately)
                     StringUtil.formatDuration(times.separateApplyingIndexesVisibleTime.milliseconds)
                   else
                     "Applied under read lock"
                )
              }

              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING)
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT)
                td(fileCount.numberOfFilesIndexedWithLoadingContent.toString())
              }
              tr {
                td("Number of too large for indexing files")
                td(fileProviderStatistics.sumOf { it.numberOfTooLargeForIndexingFiles }.toString())
              }
              tr {
                td("Number of files changed during indexing")
                td(statisticsOfChangedDuringIndexingFiles.numberOfFiles.toString())
              }
            }
          }
        }

        div(id = SECTION_SLOW_FILES_ID) {
          table(classes = "table-with-margin activity-table") {
            thead {
              tr {
                th("$SECTION_SLOW_FILES_TITLE (> ${IndexingFileSetStatistics.SLOW_FILE_PROCESSING_THRESHOLD_MS} ms)") {
                  colSpan = "5"
                }
              }
              tr {
                th("Provider name")
                th("File")
                th("Content loading time")
                th("Indexes values evaluation time")
                th("Total processing time")
              }
            }
            tbody {
              for (providerStatistic in fileProviderStatistics.filter { it.slowIndexedFiles.isNotEmpty() }) {
                val slowIndexedFiles = providerStatistic.slowIndexedFiles
                for ((index, slowFile) in slowIndexedFiles.sortedByDescending { it.processingTime.nano }.withIndex()) {
                  tr {
                    td(if (index == 0) providerStatistic.providerName else "^")
                    td(slowFile.fileName)
                    td(slowFile.contentLoadingTime.presentableDuration())
                    td(slowFile.evaluationOfIndexValueChangerTime.presentableDuration())
                    td(slowFile.processingTime.presentableDuration())
                  }
                }
              }
            }
          }
        }

        div(id = SECTION_STATS_PER_FILE_TYPE_ID) {
          table(classes = "table-with-margin activity-table") {
            thead {
              tr {
                th(SECTION_STATS_PER_FILE_TYPE_TITLE) {
                  colSpan = "7"
                }
              }
              tr {
                th("File type")
                th("Number of files")
                th("Total processing time (% of total processing time)")
                th("Content loading time (% of total content loading time)")
                th("Total files size")
                th("Total processing speed (relative to CPU time)")
                th("The biggest contributors")
              }
            }
            tbody {
              for (statsPerFileType in totalStatsPerFileType) {
                val visibleIndexingTime = JsonDuration(
                  (times.totalWallTimeWithPauses.nano * statsPerFileType.partOfTotalProcessingTime.partition).toLong()
                )
                val visibleContentLoadingTime = JsonDuration(
                  (times.contentLoadingVisibleTime.nano * statsPerFileType.partOfTotalContentLoadingTime.partition).toLong()
                )
                tr(classes = getMinorDataClass(visibleIndexingTime.milliseconds < 500)) {
                  td(statsPerFileType.fileType)
                  td(statsPerFileType.totalNumberOfFiles.toString())
                  td(
                    visibleIndexingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalProcessingTime.presentablePercentages() + ")")
                  td(
                    visibleContentLoadingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalContentLoadingTime.presentablePercentages() + ")")
                  td(statsPerFileType.totalFilesSize.presentableSize())
                  td(statsPerFileType.totalProcessingSpeed.presentableSpeed())
                  td(
                    statsPerFileType.biggestContributors.joinToString("\n") {
                      it.partOfTotalProcessingTimeOfThisFileType.presentablePercentages() + ": " +
                      it.providerName + " " +
                      it.numberOfFiles + " files of total size " +
                      it.totalFilesSize.presentableSize()
                    }
                  )
                }
              }
            }
          }
        }

        div(id = SECTION_STATS_PER_INDEXER_ID) {
          table(classes = "table-with-margin activity-table") {
            thead {
              tr {
                th(SECTION_STATS_PER_INDEXER_TITLE) {
                  colSpan = "7"
                }
              }
              tr {
                th("Index")
                th("Number of files")
                th("Part of total indexing time")
                th("Total number of files indexed by $INDEX_INFRA_EXTENSIONS")
                th("Total files size")
                th("Indexing speed (relative to CPU time)")
                th("Snapshot input mapping statistics")
              }
            }
            tbody {
              for (statsPerIndexer in totalStatsPerIndexer) {
                tr(classes = getMinorDataClass(statsPerIndexer.partOfTotalIndexingTime.partition < 0.1)) {
                  td(statsPerIndexer.indexId)
                  td(statsPerIndexer.totalNumberOfFiles.toString())
                  td(statsPerIndexer.partOfTotalIndexingTime.presentablePercentages())
                  td(statsPerIndexer.totalNumberOfFilesIndexedByExtensions.toString())
                  td(statsPerIndexer.totalFilesSize.presentableSize())
                  td(statsPerIndexer.indexValueChangerEvaluationSpeed.presentableSpeed())

                  fun JsonProjectDumbIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats.presentable(): String {
                    val hitsPercentages = JsonPercentages(totalHits, totalRequests)
                    val missesPercentages = JsonPercentages(totalMisses, totalRequests)
                    return "requests: $totalRequests, " +
                           "hits: $totalHits (${hitsPercentages.presentablePercentages()}), " +
                           "misses: $totalMisses (${missesPercentages.presentablePercentages()})"
                  }
                  td(statsPerIndexer.snapshotInputMappingStats.presentable())
                }
              }
            }
          }
        }

        val shouldPrintIndexedFiles = fileProviderStatistics.any { it.indexedFiles.orEmpty().isNotEmpty() }
        div(id = SECTION_INDEXING_ID) {
          table(classes = "table-with-margin activity-table") {
            thead {
              tr {
                th(SECTION_INDEXING_TITLE) {
                  colSpan = if (shouldPrintIndexedFiles) "7" else "6"
                }
              }
              tr {
                th("Provider name")
                th("Total processing time")
                th("Content loading time")
                th("Number of indexed files")
                th("Number of files indexed by $INDEX_INFRA_EXTENSIONS")
                th("Number of too large for indexing files")
                if (shouldPrintIndexedFiles) {
                  th("Indexed files")
                }
              }
            }
            tbody {
              for (providerStats in fileProviderStatistics) {
                tr(classes = getMinorDataClass(
                  providerStats.totalIndexingVisibleTime.milliseconds < 100 && providerStats.totalNumberOfIndexedFiles < 1000)) {
                  td(providerStats.providerName)
                  td(providerStats.totalIndexingVisibleTime.presentableDuration())
                  td(providerStats.contentLoadingVisibleTime.presentableDuration())
                  td(providerStats.totalNumberOfIndexedFiles.toString())
                  td(providerStats.totalNumberOfFilesFullyIndexedByExtensions.toString())
                  td(providerStats.numberOfTooLargeForIndexingFiles.toString())
                  if (shouldPrintIndexedFiles) {
                    td {
                      textArea(
                        providerStats.indexedFiles.orEmpty().joinToString("\n") { file ->
                          file.path.presentablePath + if (file.wasFullyIndexedByExtensions) " [by infrastructure]" else ""
                        }
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }.toString()
}

private fun DIV.printProjectNameForActivity(projectName: String) {
  div(id = SECTION_PROJECT_NAME_ID) {
    h1(classes = "aggregate-header") { text(projectName) }
  }
}


@Suppress("CssUnusedSymbol")
@Language("CSS")
private val CSS_STYLE = """
  body {
    font-family: Arial, sans-serif;
    margin: 0;
  }
  
  table, th, td {
    border: 1px solid black;
    border-collapse: collapse;
  }
  
  table {
    width: 80%;
  }
  
  table.activity-table {
     width: 100%;
  }
    
  table.narrow-activity-table {
     width: 80%;
  }
  
  table.two-columns td {
    width: 50%;
  }
  
  th, td {
    padding: 3px;
  }
  
  th {
    background: lightgrey;
  }
  
  td {
    white-space: pre-wrap;
    word-break: break-word;
  }
          
  .stats-content {
    margin-left: 20%;
  }
    
  .stats-activity-content {
    margin-left: 20%;
    margin-right: 5%;
  }
  
  .aggregate-report-content {
    margin-left: 10%;
  } 
   
  .aggregate-activity-report-content {
    margin-left: 15%;
    margin-right: 15%;
  }
  
  .aggregate-header {
    padding-top: 1em;
    padding-bottom: 1em;
    margin-bottom: 0;
  }
  
  .table-with-margin{
    margin-top: 1em;
    margin-bottom: 2em;
  }

  .navigation-bar {
    width: 15%;
    background-color: lightgrey;
    position: fixed;
    height: 100%;
  }

  div.navigation-bar ul {
    list-style-type: none;
    overflow: auto;
    margin: 0;
    padding: 0;
    font-size: 24px;
  }

  div.navigation-bar ul li a, label {
    display: block;
    color: #000;
    padding: 8px 20px;
    text-decoration: none;
  }

  label input {
    margin-left: 10px;
    width: 20px;
    height: 20px;
  }

  div.navigation-bar ul li a:hover {
    background-color: #555;
    color: white;
  }

  .minor-data {}

  .invisible {
    display: none;
  }
  
  .jetbrains-logo {
    width: 50%;
    bottom: 5%;
    position: absolute;
    left: 20%;
  }
  
  .centered-text {
    text-align: center;
  }
  
  .linkable-table-row:hover {
    background: #f2f3ff;
    outline: none;
    cursor: pointer;
  }
  
  .scanning-table-row {
    background-color: aliceblue;
  }
  
  .not-applicable-data{
    color: darkgrey;
  }
""".trimIndent()

@Language("JavaScript")
private val LINKABLE_TABLE_ROW_SCRIPT = """
  document.addEventListener("DOMContentLoaded", () => {
    const rows = document.getElementsByClassName("linkable-table-row")
    for (const row of rows) {
      const href = row.getAttribute("href")
      row.addEventListener("click", () => {
        window.open(href, "_blank");
      });
    }
  });
""".trimIndent()

@Language("JavaScript")
private val JS_SCRIPT = """
  function hideElementsHavingClass(className, hideOrShow) {
    const elements = document.getElementsByClassName(className)
    const displayType = hideOrShow ? 'none' : 'initial'
    for (const element of elements) {
      element.classList.toggle('invisible', hideOrShow)
    }
  }
""".trimIndent()

private val JETBRAINS_GRAYSCALE_LOGO_SVG = """
  <svg id="Layer_1" data-name="Layer 1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120.21407 130.10375">
    <title>logo-grey</title>
    <g>
      <path d="M120.2,68.6a4.99684,4.99684,0,0,0-1.4-3.5L55.191,2.68736a7.86372,7.86372,0,0,0-3.36731-2.261c-.03467-.012-.06812-.02612-.103-.03766-.19336-.06323-.3913-.11511-.591-.16327-.06689-.0163-.13177-.0368-.1994-.05133-.18744-.04-.37909-.06738-.57117-.09363-.0788-.0108-.15527-.0274-.23492-.03589A7.83914,7.83914,0,0,0,49.3,0a7.73961,7.73961,0,0,0-1.21088.10413c-.0235.00391-.04694.00671-.07037.0108a7.62573,7.62573,0,0,0-3.092,1.24969c-.07343.04773-.155.08575-.22668.13538L4.9,28.3c-.08221.08221-.15106.10632-.17773.16437L4.67218,28.5H4.6A11.11647,11.11647,0,0,0,.15875,39.45683l.00854.04449c.05176.28589.11011.56958.18372.84973.03052.119.06964.235.104.35284.054.181.10278.3634.16571.5412A11.15109,11.15109,0,0,0,5.3,47.1a8.82025,8.82025,0,0,0,2,.9c.4.2,45.4,18.8,45.4,18.8a4.291,4.291,0,0,0,4.4-7.3c-.06525,0-16.839-13.21332-28.69928-22.52606l21.105-19.03113,57.91815,49.58282L28.6,110.7a9.82332,9.82332,0,0,0-4.7,4.1,10.0662,10.0662,0,0,0,3.6,13.9,10.28689,10.28689,0,0,0,10.7-.2c.2-.2.5-.3.7-.5L116.9,73.2a18.32,18.32,0,0,0,1.58612-1.2663A4.74573,4.74573,0,0,0,120.2,68.6Z" transform="translate(0.01406 0.00002)" fill="#cdcdcd"/>
      <g id="_Group_" data-name="&lt;Group&gt;">
        <rect id="_Path_" data-name="&lt;Path&gt;" x="34.61406" y="37.40002" width="51" height="51"/>
        <rect id="_Path_2" data-name="&lt;Path&gt;" x="39.01406" y="78.80002" width="19.1" height="3.2" fill="#fff"/>
        <g id="_Group_2" data-name="&lt;Group&gt;">
          <path id="_Path_3" data-name="&lt;Path&gt;" d="M38.8,50.8l1.5-1.4a1.70271,1.70271,0,0,0,1.3.8q.9,0,.9-1.2V43.7h2.3V49a2.79543,2.79543,0,0,1-3.1,3.1A3.026,3.026,0,0,1,38.8,50.8Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Path_4" data-name="&lt;Path&gt;" d="M45.3,43.8H52v1.9H47.6V47h4v1.8h-4v1.3h4.5v2H45.4Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Path_5" data-name="&lt;Path&gt;" d="M55,45.8H52.5v-2h7.3v2H57.3v6.3H55Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Compound_Path_" data-name="&lt;Compound Path&gt;" d="M39,54h4.3a3.7023,3.7023,0,0,1,2.3.7,1.97822,1.97822,0,0,1,.5,1.4h0A1.95538,1.95538,0,0,1,44.8,58a1.94762,1.94762,0,0,1,1.6,2h0c0,1.4-1.2,2.3-3.1,2.3H39Zm4.8,2.6c0-.5-.4-.7-1-.7H41.3v1.5h1.4c.7-.1,1.1-.3,1.1-.8ZM43,59H41.2v1.5H43c.7,0,1.1-.3,1.1-.8h0C44.1,59.2,43.7,59,43,59Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Compound_Path_2" data-name="&lt;Compound Path&gt;" d="M46.8,54h3.9a3.51463,3.51463,0,0,1,2.7.9,2.48948,2.48948,0,0,1,.7,1.9h0a2.76053,2.76053,0,0,1-1.7,2.6l2,2.9H51.8l-1.7-2.5h-1v2.5H46.8Zm3.8,4c.8,0,1.2-.4,1.2-1h0c0-.7-.5-1-1.2-1H49.1v2Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Compound_Path_3" data-name="&lt;Compound Path&gt;" d="M56.8,54H59l3.5,8.4H60l-.6-1.5H56.2l-.6,1.5H53.2Zm2,5-.9-2.3L57,59Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Path_6" data-name="&lt;Path&gt;" d="M62.8,54h2.3v8.3H62.8Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Path_7" data-name="&lt;Path&gt;" d="M65.7,54h2.1l3.4,4.4V54h2.3v8.3h-2L68,57.8v4.6H65.7Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
          <path id="_Path_8" data-name="&lt;Path&gt;" d="M73.7,61.1,75,59.6a3.94219,3.94219,0,0,0,2.7,1c.6,0,1-.2,1-.6h0c0-.4-.3-.5-1.4-.8-1.8-.4-3.1-.9-3.1-2.6h0c0-1.5,1.2-2.7,3.2-2.7A5.33072,5.33072,0,0,1,80.8,55l-1.2,1.6a4.55346,4.55346,0,0,0-2.3-.8c-.6,0-.8.2-.8.5h0c0,.4.3.5,1.4.8,1.9.4,3.1,1,3.1,2.6h0c0,1.7-1.3,2.7-3.4,2.7A5.29336,5.29336,0,0,1,73.7,61.1Z" transform="translate(0.01406 0.00002)" fill="#fff"/>
        </g>
      </g>
    </g>
  </svg>
""".trimIndent()

private inline fun FlowContent.div(id: String, crossinline block : DIV.() -> Unit = {}) {
  div {
    this.id = id
    block()
  }
}
private inline fun TR.th(value: String, crossinline block : TH.() -> Unit = {}) {
  th {
    block()
    +value
  }
}
private inline fun TR.td(value: String, crossinline block : TD.() -> Unit = {}) {
  td {
    if (value == NOT_APPLICABLE || value == DID_NOT_START) {
      classes += "not-applicable-data"
    }
    block()
    +value
  }
}

private fun FlowOrInteractiveOrPhrasingContent.textArea( text: String) {
  textArea(rows = "10", cols = "75") {
    attributes["readonly"] = "true"
    attributes["placeholder"] = "empty"
    attributes["style"] = "white-space: pre; border: none"
    unsafe {
      +text
    }
  }
}
