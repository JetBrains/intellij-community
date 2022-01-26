// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "DuplicatedCode", "HardCodedStringLiteral")

package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedEvent
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.JsonSharedIndexDiagnosticEvent
import com.intellij.util.indexing.diagnostic.dto.*
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls

fun createAggregateHtml(
  projectName: String,
  diagnostics: List<IndexDiagnosticDumper.ExistingDiagnostic>,
  sharedIndexEvents: List<JsonSharedIndexDiagnosticEvent>,
  changedFilesPushEvents: List<ChangedFilesPushedEvent>
): String = html {
  head {
    title("Indexing diagnostics of '$projectName'")
    style(CSS_STYLE)
    script(LINKABLE_TABLE_ROW_SCRIPT)
  }
  body {
    div(className = "aggregate-report-content") {
      h1("Project name")
      text(projectName)

      div {
        h1("Indexing history")
        table(className = "centered-text") {
          appendRaw("<caption style=\"caption-side: bottom; text-align: right; font-size: 14px\">Hover for details</caption>")
          thead {
            tr {
              th("Time", colspan = "7")
              th("Files", colspan = "6")
              th("IDE", rowspan = "2")
              th("Type", rowspan = "2")
            }
            tr {
              th("Started")
              th("Total")
              th("Creating iterators")
              th("Scanning")
              th("Indexing")
              th("Content loading")
              th("Finished")
              th("Providers")
              th("Scanned")
              th("Shared indexes (w/o content loading)")
              th("Scheduled for indexing")
              th("Shared indexes (content loaded)")
              th("Total indexed (shared indexes included)")
            }
          }
          tbody {
            for (diagnostic in diagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }) {
              tr(className = "linkable-table-row", href = diagnostic.htmlFile.fileName.toString()) {
                // Time section.
                td {
                  if (diagnostic.indexingTimes.indexingReason != null) {
                    strong(diagnostic.indexingTimes.indexingReason)
                    br()
                  }
                  text(diagnostic.indexingTimes.updatingStart.presentableLocalDateTime())
                }
                td(diagnostic.indexingTimes.totalUpdatingTime.presentableDuration())
                td(diagnostic.indexingTimes.creatingIteratorsTime.presentableDuration())
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
                td(fileCount?.numberOfFileProviders?.toString() ?: NOT_APPLICABLE)
                td(fileCount?.numberOfScannedFiles?.toString() ?: NOT_APPLICABLE)
                td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringScan?.toString() ?: NOT_APPLICABLE)
                td(fileCount?.numberOfFilesScheduledForIndexingAfterScan?.toString() ?: NOT_APPLICABLE)
                td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage?.toString() ?: NOT_APPLICABLE)
                td(fileCount?.numberOfFilesIndexedWithLoadingContent?.toString() ?: NOT_APPLICABLE)

                // IDE section.
                td(diagnostic.appInfo.productCode + "-" + diagnostic.appInfo.build)

                //Indexing type section
                td(if (diagnostic.indexingTimes.wasFullIndexing) "Full" else "Partial")
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
                val lastAttach = events.filterIsInstance<JsonSharedIndexDiagnosticEvent.Attached>().maxByOrNull { it.time.instant } ?: continue
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
                  print(event)
                }
              }
              printUnified(eventsToUnify)
            }
          }
        }
      }
    }
  }
}.toString()

private fun HtmlBuilder.print(event: ChangedFilesPushedEvent) {
  tr {
    td(event.startTime.presentableLocalDateTime())
    td(event.reason)
    td(event.duration.presentableDuration())
    td(if (event.isCancelled) "cancelled" else "fully finished")
    td("1")
  }
}

private fun HtmlBuilder.printUnified(eventsToUnify: List<ChangedFilesPushedEvent>) {
  if (eventsToUnify.isEmpty()) return
  val event = eventsToUnify[0]
  if (eventsToUnify.size == 1) {
    print(event)
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

private const val SECTION_PROJECT_NAME_ID = "id-project-name"
private const val SECTION_PROJECT_NAME_TITLE = "Project name"

private const val SECTION_APP_INFO_ID = "id-app-info"
private const val SECTION_APP_INFO_TITLE = "Application info"

private const val SECTION_RUNTIME_INFO_ID = "id-runtime-info"
private const val SECTION_RUNTIME_INFO_TITLE = "Runtime"

private const val SECTION_INDEXING_INFO_ID = "id-indexing-info"
private const val SECTION_INDEXING_INFO_TITLE = "Indexing info"

private const val SECTION_SLOW_FILES_ID = "id-slow-files"
private const val SECTION_SLOW_FILES_TITLE = "Slowly indexed files"

private const val SECTION_STATS_PER_FILE_TYPE_ID = "id-stats-per-file-type"
private const val SECTION_STATS_PER_FILE_TYPE_TITLE = "Statistics per file type"

private const val SECTION_STATS_PER_INDEXER_ID = "id-stats-per-indexer"
private const val SECTION_STATS_PER_INDEXER_TITLE = "Statistics per indexer"

private const val SECTION_SCANNING_ID = "id-scanning"
private const val SECTION_SCANNING_TITLE = "Scanning"

private const val SECTION_INDEXING_ID = "id-indexing"
private const val SECTION_INDEXING_TITLE = "Indexing"

/**
 * For now we have only Shared Indexes implementation of FileBasedIndexInfrastructureExtension,
 * so for simplicity let's use this name instead of a general "index infrastructure extensions".
 */
private const val INDEX_INFRA_EXTENSIONS = "shared indexes"

private const val TITLE_NUMBER_OF_FILE_PROVIDERS = "Number of file providers"
private const val TITLE_NUMBER_OF_SCANNED_FILES = "Number of scanned files"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRA_EXTENSIONS_DURING_SCAN = "Number of files indexed by $INDEX_INFRA_EXTENSIONS during the scan (without loading content)"
private const val TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN = "Number of files scheduled for indexing after scanning"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING = "Number of files indexed by $INDEX_INFRA_EXTENSIONS during the indexing stage (with loading content)"
private const val TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT = "Number of files indexed during the indexing stage with loading content (including indexed by $INDEX_INFRA_EXTENSIONS)"

private fun HtmlBuilder.printRuntimeInfo(runtimeInfo: JsonRuntimeInfo) {
  div(id = SECTION_RUNTIME_INFO_ID) {
    h1(SECTION_RUNTIME_INFO_TITLE)
    table(className = "two-columns") {
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

private fun HtmlBuilder.printAppInfo(appInfo: JsonIndexDiagnosticAppInfo) {
  div(id = SECTION_APP_INFO_ID) {
    h1(SECTION_APP_INFO_TITLE)
    table(className = "two-columns") {
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

private const val hideMinorDataInitial = true
private fun getMinorDataClass(isMinor: Boolean) = if (isMinor) "minor-data" + (if (hideMinorDataInitial) " invisible" else "") else ""

fun JsonIndexDiagnostic.generateHtml(): String {
  return html {
    head {
      title("Indexing diagnostics of '${projectIndexingHistory.projectName}'")
      style(CSS_STYLE)
      script(JS_SCRIPT)
    }
    body {
      div(className = "navigation-bar") {
        ul {
          li { link("#$SECTION_PROJECT_NAME_ID", SECTION_PROJECT_NAME_TITLE) }
          li { link("#$SECTION_APP_INFO_ID", SECTION_APP_INFO_TITLE) }
          li { link("#$SECTION_RUNTIME_INFO_ID", SECTION_RUNTIME_INFO_TITLE) }
          li { link("#$SECTION_INDEXING_INFO_ID", SECTION_INDEXING_INFO_TITLE) }
          li { link("#$SECTION_SLOW_FILES_ID", SECTION_SLOW_FILES_TITLE) }
          li { link("#$SECTION_STATS_PER_FILE_TYPE_ID", SECTION_STATS_PER_FILE_TYPE_TITLE) }
          li { link("#$SECTION_STATS_PER_INDEXER_ID", SECTION_STATS_PER_INDEXER_TITLE) }
          li { link("#$SECTION_SCANNING_ID", SECTION_SCANNING_TITLE) }
          li { link("#$SECTION_INDEXING_ID", SECTION_INDEXING_TITLE) }
        }
        hr("solid")
        ul {
          li {
            label(forId = "id-hide-minor-data-checkbox") {
              text("Hide minor data")
              input(id = "id-hide-minor-data-checkbox", type = "checkbox", onClick = "hideElementsHavingClass('minor-data', this.checked)",
                    style = "padding-left: 10px", checked = hideMinorDataInitial)
            }
          }
        }
        div(className = "jetbrains-logo") {
          rawText(JETBRAINS_GRAYSCALE_LOGO_SVG)
        }
      }

      div(className = "stats-content") {
        div(id = SECTION_PROJECT_NAME_ID) {
          h1(SECTION_PROJECT_NAME_TITLE)
          text(projectIndexingHistory.projectName)
        }

        printAppInfo(appInfo)
        printRuntimeInfo(runtimeInfo)

        div(id = SECTION_INDEXING_INFO_ID) {
          h1(SECTION_INDEXING_INFO_TITLE)
          table(className = "two-columns") {
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
              tr { td("Full or partial"); td(if (times.wasFullIndexing) "full" else "partial") }
              tr { td("Finished at"); td(times.updatingEnd.presentableLocalDateTime()) }
              tr { td("Cancelled?"); td(times.wasInterrupted.toString()) }
              tr { td("Suspended time"); td(times.totalSuspendedTime.presentableDuration()) }
              tr { td("Total time"); td(times.totalUpdatingTime.presentableDuration()) }
              tr { td("Indexing time"); td(times.indexingTime.presentableDuration()) }
              tr { td("Iterators creation time"); td(times.creatingIteratorsTime.presentableDuration()) }
              if (IndexDiagnosticDumper.shouldProvideVisibleAndAllThreadsTimeInfo) {
                tr {
                  td("Indexing visible time");
                  td(JsonDuration(
                    projectIndexingHistory.fileProviderStatistics.sumOf { stat -> stat.totalIndexingVisibleTime.nano }).presentableDuration())
                }
                tr {
                  td("All threads time to visible time ratio");
                  td(String.format("%.2f", projectIndexingHistory.visibleTimeToAllThreadTimeRatio))
                }
              }
              tr { td("Scanning time"); td(times.scanFilesTime.presentableDuration()) }
              tr { td("Content loading time"); td(times.contentLoadingVisibleTime.presentableDuration()) }
              tr { td("Pushing properties time"); td(times.pushPropertiesTime.presentableDuration()) }
              tr { td("Running extensions time"); td(times.indexExtensionsTime.presentableDuration()) }
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
                    td(slowFile.indexingTime.presentableDuration())
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
                th("Indexing time")
                th("Content loading time")
                th("Total files size")
                th("Indexing speed")
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
                tr(className = getMinorDataClass(visibleIndexingTime.milliseconds < 500)) {
                  td(statsPerFileType.fileType)
                  td(statsPerFileType.totalNumberOfFiles.toString())
                  td(visibleIndexingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalProcessingTime.presentablePercentages() + ")")
                  td(visibleContentLoadingTime.presentableDuration() + " (" + statsPerFileType.partOfTotalContentLoadingTime.presentablePercentages() + ")")
                  td(statsPerFileType.totalFilesSize.presentableSize())
                  td(statsPerFileType.indexingSpeed.presentableSpeed())
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
                tr(className = getMinorDataClass(statsPerIndexer.partOfTotalIndexingTime.partition < 0.1)) {
                  td(statsPerIndexer.indexId)
                  td(statsPerIndexer.totalNumberOfFiles.toString())
                  td(statsPerIndexer.partOfTotalIndexingTime.presentablePercentages())
                  td(statsPerIndexer.totalNumberOfFilesIndexedByExtensions.toString())
                  td(statsPerIndexer.totalFilesSize.presentableSize())
                  td(statsPerIndexer.indexingSpeed.presentableSpeed())

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
                tr(className = getMinorDataClass(scanningStats.scanningTime.milliseconds < 100 && scanningStats.numberOfScannedFiles < 1000)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.statusTime.presentableDuration())
                  td(scanningStats.scanningTime.presentableDuration())
                  td(scanningStats.timeProcessingUpToDateFiles.presentableDuration())
                  td(scanningStats.timeUpdatingContentLessIndexes.presentableDuration())
                  td(scanningStats.timeIndexingWithoutContent.presentableDuration())
                  if (shouldPrintProviderRoots) {
                    td {
                      textarea {
                        rawText(
                          scanningStats.roots.sorted().joinToString("\n")
                        )
                      }
                    }
                  }
                  if (shouldPrintScannedFiles) {
                    td {
                      textarea {
                        rawText(
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

        val shouldPrintIndexedFiles = projectIndexingHistory.fileProviderStatistics.any { it.indexedFiles.orEmpty().isNotEmpty() }
        div(id = SECTION_INDEXING_ID) {
          h1(SECTION_INDEXING_TITLE)
          table {
            thead {
              tr {
                th("Provider name")
                th("Indexing time")
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
                tr(className = getMinorDataClass(providerStats.totalIndexingVisibleTime.milliseconds < 100 && providerStats.totalNumberOfIndexedFiles < 1000)) {
                  td(providerStats.providerName)
                  td(providerStats.totalIndexingVisibleTime.presentableDuration())
                  td(providerStats.contentLoadingVisibleTime.presentableDuration())
                  td(providerStats.totalNumberOfIndexedFiles.toString())
                  td(providerStats.totalNumberOfFilesFullyIndexedByExtensions.toString())
                  td(providerStats.numberOfTooLargeForIndexingFiles.toString())
                  if (shouldPrintIndexedFiles) {
                    td {
                      textarea {
                        rawText(providerStats.indexedFiles.orEmpty().joinToString("\n") { file ->
                          file.path.presentablePath + if (file.wasFullyIndexedByExtensions) " [by infrastructure]" else ""
                        })
                      }
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
  
  .aggregate-report-content {
    margin-left: 10%;
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

private fun createTag(body: HtmlBuilder.() -> Unit, tag: Element): Element {
  val tagBuilder = HtmlBuilder()
  tagBuilder.body()
  return tagBuilder.toFragment().wrapWith(tag)
}

private fun HtmlBuilder.text(@Nls text: String) = append(text)
private fun HtmlBuilder.rawText(@Nls text: String) = appendRaw(text)
private fun HtmlBuilder.title(@Nls title: String) = append(HtmlChunk.text(title).wrapWith(tag("title")))
private fun HtmlBuilder.strong(@Nls text: String) = append(HtmlChunk.text(text).wrapWith(tag("strong")))

private fun HtmlBuilder.style(@Nls style: String) = append(styleTag(style))
private fun HtmlBuilder.script(@Nls script: String) = append(tag("script").addRaw(script))

private fun Element.addAttrIfNotEmpty(key: String, value: String): Element =
  if (value.isEmpty()) this else this.attr(key, value)

private infix operator fun HtmlBuilder.plus(@Nls text: String): HtmlBuilder = text(text)
private fun HtmlBuilder.h1(@Nls title: String) = append(HtmlChunk.text(title).wrapWith(tag("h1")))

private fun HtmlBuilder.hr(className: String) = append(HtmlChunk.hr().attr("class", className))

private fun HtmlBuilder.table(className: String = "", body: HtmlBuilder.() -> Unit) = append(
  createTag(body, tag("table").addAttrIfNotEmpty("class", className)))

private fun HtmlBuilder.thead(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("thead")))
private fun HtmlBuilder.tbody(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("tbody")))
private fun HtmlBuilder.tr(className: String = "", href: String = "", body: HtmlBuilder.() -> Unit) = append(
  createTag(body, tag("tr").addAttrIfNotEmpty("class", className).addAttrIfNotEmpty("href", href)))

private fun HtmlBuilder.th(body: HtmlBuilder.() -> Unit, colspan: String = "", rowspan: String = "") = append(createTag(body, tag("th")
  .addAttrIfNotEmpty("colspan", colspan).addAttrIfNotEmpty("rowspan", rowspan))
)

private fun HtmlBuilder.th(@Nls text: String, colspan: String = "", rowspan: String = "") = th({ text(text) }, colspan, rowspan)
private fun HtmlBuilder.td(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("td")))
private fun HtmlBuilder.td(@Nls text: String) = td { text(text) }

private fun HtmlBuilder.ul(body: HtmlBuilder.() -> Unit) = append(createTag(body, ul()))
private fun HtmlBuilder.li(body: HtmlBuilder.() -> Unit) = append(createTag(body, li()))

private fun HtmlBuilder.textarea(
  columns: Int = 75,
  rows: Int = 10,
  body: HtmlBuilder.() -> Unit
) = append(
  createTag(
    body,
    tag("textarea")
      .attr("cols", columns)
      .attr("rows", rows)
      .attr("readonly", "true")
      .attr("placeholder", "empty")
      .attr("style", "white-space: pre; border: none")
  ))

private fun HtmlBuilder.textarea(@Nls text: String) = textarea { rawText(text) }

private fun HtmlBuilder.label(forId: String, body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("label").attr("for", forId)))
private fun HtmlBuilder.input(id: String, type: String, onClick: String = "", style: String = "", checked: Boolean = false) =
  append(raw("""
     <input id="$id" type="$type" style="$style" ${if (checked) "checked" else ""} onclick="$onClick"/>
  """.trimIndent()))

private fun HtmlBuilder.link(target: String, text: String) = append(HtmlBuilder().appendLink(target, text))
private fun HtmlBuilder.div(className: String = "", id: String = "", body: HtmlBuilder.() -> Unit) = append(
  createTag(body, div().addAttrIfNotEmpty("class", className).addAttrIfNotEmpty("id", id)))

private fun HtmlBuilder.head(head: HtmlBuilder.() -> Unit) = append(createTag(head, HtmlChunk.head()))
private fun HtmlBuilder.body(body: HtmlBuilder.() -> Unit) = append(createTag(body, HtmlChunk.body()))
private fun HtmlBuilder.html(body: HtmlBuilder.() -> Unit) = createTag(body, html())
private fun html(body: HtmlBuilder.() -> Unit) = HtmlBuilder().html(body)