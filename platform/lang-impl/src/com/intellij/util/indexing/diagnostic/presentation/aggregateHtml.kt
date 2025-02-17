// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.asSafely
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedEvent
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.JsonSharedIndexDiagnosticEvent
import com.intellij.util.indexing.diagnostic.dto.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.nio.charset.StandardCharsets
import java.util.*

internal fun createAggregateActivityHtml(
  target: Appendable,
  projectName: String,
  filesAndDiagnostics: List<IndexDiagnosticDumper.FilesAndDiagnostic>,
  sharedIndexEvents: List<JsonSharedIndexDiagnosticEvent>,
  changedFilesPushEvents: List<ChangedFilesPushedEvent>
) {
  target.appendHTML().html {
    head {
      title("Indexing diagnostics of '$projectName'")
      style {
        unsafe {
          +INDEX_DIAGNOSTIC_CSS_STYLES
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
              for ((_, htmlFile, diagnostic) in filesAndDiagnostics.sortedByDescending { it.diagnostic.projectIndexingActivityHistory.times.updatingStart.instant }) {
                val times = diagnostic.projectIndexingActivityHistory.times

                val classes = if (times is JsonProjectScanningHistoryTimes) {
                  "linkable-table-row scanning-table-row"
                }
                else {
                  "linkable-table-row"
                }
                tr(classes = classes) {
                  attributes["href"] = htmlFile.fileName.toString()
                  printIndexingActivityRow(times, diagnostic.projectIndexingActivityHistory.fileCount)
                }
              }
            }
          }
        }

        div {
          table(classes = "table-with-margin activity-table metrics-table") {
            thead {
              tr {
                th("Metrics") {
                  colSpan = "2"
                }
              }
            }
            tbody {
              val metrics = IndexingMetrics(filesAndDiagnostics.map { it.diagnostic })
              for (metric in metrics.getListOfIndexingMetrics()) {
                tr {
                  when (metric) {
                    is IndexingMetric.Duration -> {
                      td(metric.name)
                      td(StringUtil.formatDuration(metric.durationMillis.toLong()))
                    }
                    is IndexingMetric.Counter -> {
                      td(metric.name)
                      td(metric.value.toString())
                    }
                  }
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
    if (times.isCancelled) {
      strong("Cancelled")
      br()
    }
    text(times.updatingEnd.presentableLocalDateTime())
  }

  // Total time
  if (times.isCancelled) {
    td(classes = "red-text") {
      +times.totalWallTimeWithPauses.presentableDuration()
      +"\ncancelled"
    }
  }
  else {
    td(times.totalWallTimeWithPauses.presentableDuration())
  }
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
      td {
        text(fileCount.numberOfFilesIndexedWithLoadingContent.toString())

        if (fileCount.numberOfChangedDuringIndexingFiles > 0) {
          br()
          text("(incl. ${fileCount.numberOfChangedDuringIndexingFiles} changed in VFS)")
        }

        if (fileCount.numberOfNothingToWriteFiles > 0) {
          br()
          text("(incl. ${fileCount.numberOfNothingToWriteFiles} with nothing to write)")
        }
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

private val LINKABLE_TABLE_ROW_SCRIPT : String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/table-row.js")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }