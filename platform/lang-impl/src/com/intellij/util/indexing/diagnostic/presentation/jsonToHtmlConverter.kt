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
import java.nio.charset.StandardCharsets
import java.util.*

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
          text("(incl. ${fileCount.numberOfChangedDuringIndexingFiles} changed in VFS)")
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

private const val SECTION_SCANNING_CONCURRENT_PART_ID = "id-scanning-concurrent-part"
private const val SECTION_SCANNING_CONCURRENT_PART_TITLE = "Concurrent part of scanning"

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
            a("#$SECTION_SCANNING_CONCURRENT_PART_ID") {
              text(SECTION_SCANNING_CONCURRENT_PART_TITLE)
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

              tr { td(TITLE_NUMBER_OF_FILE_PROVIDERS); td(fileCount.numberOfFileProviders.toString()) }
              tr { td(TITLE_NUMBER_OF_SCANNED_FILES); td(fileCount.numberOfScannedFiles.toString()) }
              tr {
                td("Number of files indexed by $INDEX_INFRA_EXTENSIONS (without loading content, part of checking files)")
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan.toString())
              }
              tr {
                td(TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN)
                td(fileCount.numberOfFilesScheduledForIndexingAfterScan.toString())
              }
            }
          }
        }

        div(id = SECTION_SCANNING_CONCURRENT_PART_ID) {
          text("Time in this table is sum of CPU times on threads with pauses unless stated otherwise.")
          table(classes = "two-columns table-with-margin narrow-activity-table") {
            thead {
              tr { th(SECTION_SCANNING_CONCURRENT_PART_TITLE) { colSpan = "2" } }
            }
            tbody {
              tr {
                td("Wall time w/o pauses")
                td(times.concurrentHandlingWallTimeWithoutPauses.presentableDuration())
              }
              tr {
                td("Wall time with pauses")
                td(times.concurrentHandlingWallTimeWithoutPauses.presentableDuration())
              }
              tr {
                td("Total time (sum of CPU times with pauses on many threads)")
                td(times.concurrentHandlingCPUTimeWithPauses.presentableDuration())
              }
              tr {
                td("Time of iterating VFS and applying scanners")
                td(times.concurrentIterationAndScannersApplicationCPUTimeWithPauses.presentableDuration())
              }
              tr {
                td("Time of checking files")
                td(times.concurrentFileCheckCPUTimeWithPauses.presentableDuration())
              }
              tr {
                td("Time of running $INDEX_INFRA_EXTENSIONS (without loading content, part of checking files)")
                td(times.indexExtensionsTime.presentableDuration())
              }
            }
          }
        }

        val shouldPrintScannedFiles = scanningStatistics.any { it.scannedFiles.orEmpty().isNotEmpty() }
        val shouldPrintProviderRoots = scanningStatistics.any { it.roots.isNotEmpty() }
        div(id = SECTION_SCANNING_ID) {
          h1 { text(SECTION_SCANNING_PER_PROVIDER_TITLE) }
          text("Providers are handled during concurrent part of scanning, however each provider is handled in a single thread.")
          table(classes = "table-with-margin activity-table") {
            thead {
              var rowHeaderNumber = 3
              var timeColumnsNumber = 7
              tr {
                fun thNonTime(text: String) = th(text) { rowSpan = rowHeaderNumber.toString() }
                thNonTime("Provider name")
                thNonTime("Number of scanned files")
                thNonTime("Number of files scheduled for indexing")
                thNonTime("Number of files fully indexed by $INDEX_INFRA_EXTENSIONS")
                thNonTime("Number of double-scanned skipped files")
                th("Time") { colSpan = timeColumnsNumber.toString() }
                if (shouldPrintProviderRoots) {
                  thNonTime("Roots")
                }
                if (shouldPrintScannedFiles) {
                  thNonTime("Scanned files")
                }
              }
              tr {
                rowHeaderNumber--
                timeColumnsNumber -= 2
                th("Total time") { rowSpan = rowHeaderNumber.toString() }
                th("VFS iteration and scanners application") { rowSpan = rowHeaderNumber.toString() }
                th("Files' check") { colSpan = timeColumnsNumber.toString() }
              }
              tr {
                th("Total time")
                th("Getting files' statuses")
                th("Processing up-to-date files")
                th("Updating content-less indexes")
                th("Indexing by $INDEX_INFRA_EXTENSIONS without content")
              }
            }
            tbody {
              for (scanningStats in scanningStatistics) {
                tr(classes = getMinorDataClass(
                  scanningStats.totalCPUTimeWithPauses.milliseconds < 100 && scanningStats.numberOfScannedFiles < 1000)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.totalCPUTimeWithPauses.presentableDuration())
                  td(scanningStats.iterationAndScannersApplicationTime.presentableDuration())
                  td(scanningStats.filesCheckTime.presentableDuration())
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
              tr {
                td {
                  text("Time of retrieving list of the files changed in VFS")
                  br()
                  small { text("Indexing handles both files found by scanning(s) and files reported as changed by VFS") }
                }
                td(times.retrievingChangedDuringIndexingFilesTime.presentableDuration())
              }
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
                td("Number of files changed in VFS")
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

private val CSS_STYLE: String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/styles.css")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }

private val LINKABLE_TABLE_ROW_SCRIPT : String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/table-row.js")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }

private val JS_SCRIPT: String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/hide-elements.js")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }

private val JETBRAINS_GRAYSCALE_LOGO_SVG: String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/logo.svg")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }

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
