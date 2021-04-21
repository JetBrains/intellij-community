// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused", "DuplicatedCode", "HardCodedStringLiteral")

package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexingJobStatistics
import com.intellij.util.indexing.diagnostic.dto.*
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls

fun createAggregateHtml(
  projectName: String,
  diagnostics: List<IndexDiagnosticDumper.ExistingDiagnostic>
): String {
  val appInfo = JsonIndexDiagnosticAppInfo.create()
  val runtimeInfo = JsonRuntimeInfo.create()
  return html {
    head {
      title("Indexing diagnostics of '$projectName'")
      style(CSS_STYLE)
    }
    body {
      h1("Project name")
      text(projectName)

      printAppInfo(appInfo)
      printRuntimeInfo(runtimeInfo)

      h1("Indexing history")
      table {
        thead {
          tr {
            th("Started")
            th("Total time")
            th("Scanning time")
            th("Indexing time")
            th("Content loading time")
            th(TITLE_NUMBER_OF_FILE_PROVIDERS)
            th(TITLE_NUMBER_OF_SCANNED_FILES)
            th(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRA_EXTENSIONS_DURING_SCAN)
            th(TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN)
            th(TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING)
            th(TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT)
            th("Details")
          }
        }
        tbody {
          for (diagnostic in diagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }) {
            tr {
              td(diagnostic.indexingTimes.updatingStart.presentableDateTime())
              td(diagnostic.indexingTimes.totalUpdatingTime.presentableDuration())
              td(diagnostic.indexingTimes.scanFilesTime.presentableDuration())
              td(diagnostic.indexingTimes.indexingTime.presentableDuration())
              td(diagnostic.indexingTimes.contentLoadingTime.presentableDuration())

              val fileCount = diagnostic.fileCount
              td(fileCount?.numberOfFileProviders?.toString() ?: "N/A")
              td(fileCount?.numberOfScannedFiles?.toString() ?: "N/A")
              td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringScan?.toString() ?: "N/A")
              td(fileCount?.numberOfFilesScheduledForIndexingAfterScan?.toString() ?: "N/A")
              td(fileCount?.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage?.toString() ?: "N/A")
              td(fileCount?.numberOfFilesIndexedWithLoadingContent?.toString() ?: "N/A")

              td {
                link(diagnostic.htmlFile.fileName.toString(), "details")
              }
            }
          }
        }
      }
    }
  }.toString()
}

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

private const val TITLE_NUMBER_OF_FILE_PROVIDERS = "Number of file providers"
private const val TITLE_NUMBER_OF_SCANNED_FILES = "Number of scanned files"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRA_EXTENSIONS_DURING_SCAN = "Number of files indexed by infrastructure extensions during the scan (without loading content)"
private const val TITLE_NUMBER_OF_FILES_SCHEDULED_FOR_INDEXING_AFTER_SCAN = "Number of files scheduled for indexing after scanning"
private const val TITLE_NUMBER_OF_FILES_INDEXED_BY_INFRASTRUCTURE_EXTENSIONS_DURING_INDEXING = "Number of files indexed by infrastructure extensions during the indexing stage (with loading content)"
private const val TITLE_NUMBER_OF_FILES_INDEXED_WITH_LOADING_CONTENT = "Number of files indexed during the indexing stage with loading content (including indexed by infrastructure extension)"

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
        tr { td("Build date"); td(appInfo.buildDate.presentableDateTime()) }
        tr { td("Product code"); td(appInfo.productCode) }
        tr { td("Generated"); td(appInfo.generated.presentableDateTime()) }
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
              tr { th("Name"); th("Time") }
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
                td(projectIndexingHistory.fileProviderStatistics.sumBy { it.numberOfTooLargeForIndexingFiles }.toString())
              }

              val times = projectIndexingHistory.times
              tr { td("Total updating time"); td(times.totalUpdatingTime.presentableDuration()) }
              tr { td("Interrupted"); td(times.wasInterrupted.toString()) }
              tr { td("Started at"); td(times.updatingStart.presentableDateTime()) }
              tr { td("Finished at"); td(times.updatingEnd.presentableDateTime()) }
              tr { td("Suspended time"); td(times.totalSuspendedTime.presentableDuration()) }
              tr { td("Indexing time"); td(times.indexingTime.presentableDuration()) }
              tr { td("Scanning time"); td(times.scanFilesTime.presentableDuration()) }
              tr { td("Content loading time"); td(times.contentLoadingTime.presentableDuration()) }
              tr { td("Pushing properties time"); td(times.pushPropertiesTime.presentableDuration()) }
              tr { td("Running extensions time"); td(times.indexExtensionsTime.presentableDuration()) }
            }
          }
        }

        div(id = SECTION_SLOW_FILES_ID) {
          h1("$SECTION_SLOW_FILES_TITLE (> ${IndexingJobStatistics.SLOW_FILE_PROCESSING_THRESHOLD_MS} ms)")
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
                  (projectIndexingHistory.times.contentLoadingTime.nano * statsPerFileType.partOfTotalContentLoadingTime.partition).toLong()
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
                th("Total number of files indexed by extensions")
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
        div(id = SECTION_SCANNING_ID) {
          h1(SECTION_SCANNING_TITLE)
          table {
            thead {
              tr {
                th("Provider name")
                th("Number of scanned files")
                th("Number of files scheduled for indexing")
                th("Number of files fully indexed by infrastructure extensions")
                th("Number of double-scanned skipped files")
                th("Scanning time")
                th("Time processing up-to-date files")
                th("Time updating content-less indexes")
                th("Time indexing without content")
                if (shouldPrintScannedFiles) {
                  th("Scanned files")
                }
              }
            }
            tbody {
              for (scanningStats in projectIndexingHistory.scanningStatistics) {
                tr(className = getMinorDataClass(scanningStats.scanningTime.milliseconds < 100)) {
                  td(scanningStats.providerName)
                  td(scanningStats.numberOfScannedFiles.toString())
                  td(scanningStats.numberOfFilesForIndexing.toString())
                  td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
                  td(scanningStats.numberOfSkippedFiles.toString())
                  td(scanningStats.scanningTime.presentableDuration())
                  td(scanningStats.timeProcessingUpToDateFiles.presentableDuration())
                  td(scanningStats.timeUpdatingContentLessIndexes.presentableDuration())
                  td(scanningStats.timeIndexingWithoutContent.presentableDuration())
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
                th("Number of files indexed by infrastructure extensions")
                th("Number of too large for indexing files")
                if (shouldPrintIndexedFiles) {
                  th("Indexed files")
                }
              }
            }
            tbody {
              for (providerStats in projectIndexingHistory.fileProviderStatistics) {
                tr(className = getMinorDataClass(providerStats.totalIndexingTime.milliseconds < 100)) {
                  td(providerStats.providerName)
                  td(providerStats.totalIndexingTime.presentableDuration())
                  td(providerStats.contentLoadingTime.presentableDuration())
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

private fun createTag(body: HtmlBuilder.() -> Unit, tag: Element): Element {
  val tagBuilder = HtmlBuilder()
  tagBuilder.body()
  return tagBuilder.toFragment().wrapWith(tag)
}

private fun HtmlBuilder.text(@Nls text: String) = append(text)
private fun HtmlBuilder.rawText(@Nls text: String) = appendRaw(text)
private fun HtmlBuilder.title(@Nls title: String) = append(HtmlChunk.text(title).wrapWith(tag("title")))

private fun HtmlBuilder.style(@Nls style: String) = append(styleTag(style))
private fun HtmlBuilder.script(@Nls script: String) = append(tag("script").addRaw(script))

private fun Element.addAttrIfNotEmpty(key: String, value: String): Element =
  if (value.isEmpty()) this else this.attr(key, value)

private infix operator fun HtmlBuilder.plus(@Nls text: String): HtmlBuilder = text(text)
private fun HtmlBuilder.h1(@Nls title: String) = append(HtmlChunk.text(title).wrapWith(tag("h1")))

private fun HtmlBuilder.hr(className: String) = append(HtmlChunk.hr().attr("class", className))

private fun HtmlBuilder.table(className: String = "", body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("table").addAttrIfNotEmpty("class", className)))
private fun HtmlBuilder.thead(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("thead")))
private fun HtmlBuilder.tbody(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("tbody")))
private fun HtmlBuilder.tr(className: String = "", body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("tr").addAttrIfNotEmpty("class", className)))
private fun HtmlBuilder.th(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("th")))
private fun HtmlBuilder.th(@Nls text: String) = th { text(text) }
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
private fun HtmlBuilder.div(className: String = "", id: String = "", body: HtmlBuilder.() -> Unit) = append(createTag(body, div().addAttrIfNotEmpty("class", className).addAttrIfNotEmpty("id", id)))
private fun HtmlBuilder.head(head: HtmlBuilder.() -> Unit) = append(createTag(head, HtmlChunk.head()))
private fun HtmlBuilder.body(body: HtmlBuilder.() -> Unit) = append(createTag(body, HtmlChunk.body()))
private fun HtmlBuilder.html(body: HtmlBuilder.() -> Unit) = createTag(body, html())
private fun html(body: HtmlBuilder.() -> Unit) = HtmlBuilder().html(body)