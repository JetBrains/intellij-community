// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "HardCodedStringLiteral")

package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup.IndexingActivityType
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.dto.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.nio.charset.StandardCharsets
import java.util.*

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
 * so for simplicity let's use this name instead of quite general name "index infrastructure extensions".
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

fun JsonIndexingActivityDiagnostic.generateHtml(target: Appendable): String {
  return when (type) {
    IndexingActivityType.Scanning ->
      (this.projectIndexingActivityHistory as JsonProjectScanningHistory).generateScanningHtml(target, appInfo, runtimeInfo)
    IndexingActivityType.DumbIndexing ->
      (this.projectIndexingActivityHistory as JsonProjectDumbIndexingHistory).generateDumbIndexingHtml(target, appInfo, runtimeInfo)
  }
}


private fun JsonProjectScanningHistory.generateScanningHtml(target: Appendable,
                                                            appInfo: JsonIndexDiagnosticAppInfo,
                                                            runtimeInfo: JsonRuntimeInfo): String {
  return target.appendHTML().html {
    head {
      title("Indexing diagnostics of '${projectName}'")
      style {
        unsafe {
          +INDEX_DIAGNOSTIC_CSS_STYLES
        }
      }
      script {
        unsafe {
          +INDEX_DIAGNOSTIC_HIDE_ELEMENTS_SCRIPT
        }
      }
    }
    body {
      div(classes = "navigation-bar") {
        ul {
          printProjectAppOverviewNavigation()
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
        printMinorDataCheckbox()
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
              tr { td("Is cancelled"); td(times.isCancelled.toString()) }
              tr { td("Cancellation reason"); td(times.cancellationReason ?: "") }
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
          text("Time in this table is the sum of wall times on all threads with pauses unless stated otherwise.")
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
                td("Total time (sum of wall times with pauses on many threads)")
                td(times.concurrentHandlingSumOfThreadTimesWithPauses.presentableDuration())
              }
              tr {
                td("Time of iterating VFS and applying scanners")
                td(times.concurrentIterationAndScannersApplicationSumOfThreadTimesWithPauses.presentableDuration())
              }
              tr {
                td("Time of checking files")
                td(times.concurrentFileCheckSumOfThreadTimesWithPauses.presentableDuration())
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
          br()
          text("Some data can be hidden if 'Hide minor data' checkbox on the left panel is checked.")
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
                  scanningStats.totalOneThreadTimeWithPauses.milliseconds < 100 && scanningStats.numberOfScannedFiles < 1000)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.totalOneThreadTimeWithPauses.presentableDuration())
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

private fun DIV.printMinorDataCheckbox() {
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
          +INDEX_DIAGNOSTIC_CSS_STYLES
        }
      }
      script {
        unsafe {
          +INDEX_DIAGNOSTIC_HIDE_ELEMENTS_SCRIPT
        }
      }
    }
    body {
      div(classes = "navigation-bar") {
        ul {
          printProjectAppOverviewNavigation()
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
        printMinorDataCheckbox()
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
              tr { td("Is cancelled"); td(times.isCancelled.toString()) }
              tr { td("Cancellation reason"); td(times.cancellationReason ?: "") }
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
                td(StringUtil.formatDuration(times.separateApplyingIndexesVisibleTime.milliseconds))
              }

              tr {
                td(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING)
                td(fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage.toString())
              }
              tr {
                td("Number of files for which there was nothing to write (indexes were already up-to-date)")
                td(fileCount.numberOfNothingToWriteFiles.toString())
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
                th("Total processing speed (relative to the sum of wall times of all threads)")
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
                th("Indexing speed (relative to the sum of wall times on multiple threads)")
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

private fun UL.printProjectAppOverviewNavigation() {
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
}

private fun DIV.printProjectNameForActivity(projectName: String) {
  div(id = SECTION_PROJECT_NAME_ID) {
    h1(classes = "aggregate-header") { text(projectName) }
  }
}

private inline fun FlowContent.div(id: String, crossinline block : DIV.() -> Unit = {}) {
  div {
    this.id = id
    block()
  }
}

private fun FlowOrInteractiveOrPhrasingContent.textArea(text: String) {
  textArea(rows = "10", cols = "75") {
    attributes["readonly"] = "true"
    attributes["placeholder"] = "empty"
    attributes["style"] = "white-space: pre; border: none"
    unsafe {
      +text
    }
  }
}

private val INDEX_DIAGNOSTIC_HIDE_ELEMENTS_SCRIPT: String
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