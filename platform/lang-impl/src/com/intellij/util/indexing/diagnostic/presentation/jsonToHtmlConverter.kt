// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.dto.JsonIndexDiagnostic
import org.jetbrains.annotations.Nls

@Suppress("HardCodedStringLiteral")
fun JsonIndexDiagnostic.generateHtml(): String {
  return html {
    body {
      h1("Project name")
      text(projectIndexingHistory.projectName)

      h1("Application info")
      table {
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

      h1("Runtime")
      table {
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

      h1("Indexing info")
      table {
        thead {
          tr { th("Name"); th("Time") }
        }
        tbody {
          tr { td("Number of file providers"); td(projectIndexingHistory.numberOfFileProviders.toString()) }
          tr { td("Number of files"); td(projectIndexingHistory.totalNumberOfFiles.toString()) }
          tr { td("Number of up-to-date files"); td(projectIndexingHistory.totalNumberOfUpToDateFiles.toString()) }
          tr { td("Number of files indexed by infrastructure extensions"); td(projectIndexingHistory.totalNumberOfFilesIndexedByInfrastructureExtensions.toString()) }

          val times = projectIndexingHistory.times
          tr { td("Total updating time"); td(times.totalUpdatingTime.presentableDuration()) }
          tr { td("Interrupted"); td(times.wasInterrupted.toString()) }
          tr { td("Started at"); td(times.updatingStart.presentableDateTime()) }
          tr { td("Finished at"); td(times.updatingEnd.presentableDateTime()) }
          tr { td("Suspended time"); td(times.totalSuspendedTime.presentableDuration()) }
          tr { td("Indexing time"); td(times.indexingTime.presentableDuration()) }
          tr { td("Scanning time"); td(times.scanFilesTime.presentableDuration()) }
          tr { td("Pushing properties time"); td(times.pushPropertiesTime.presentableDuration()) }
          tr { td("Running extensions time"); td(times.indexExtensionsTime.presentableDuration()) }
        }
      }

      h1("Statistics per file type")
      table {
        thead {
          tr {
            th("File type")
            th("Number of files")
            th("Part of total indexing time")
            th("Part of total content loading time")
            th("Total files size")
            th("Indexing speed")
            th("The biggest contributors")
          }
        }
        tbody {
          for (statsPerFileType in projectIndexingHistory.totalStatsPerFileType) {
            tr {
              td(statsPerFileType.fileType)
              td(statsPerFileType.totalNumberOfFiles.toString())
              td(statsPerFileType.partOfTotalIndexingTime.presentablePercentages())
              td(statsPerFileType.partOfTotalContentLoadingTime.presentablePercentages())
              td(statsPerFileType.totalFilesSize.presentableSize())
              td(statsPerFileType.indexingSpeed.presentableSpeed())
              td(
                statsPerFileType.biggestContributors.joinToString("\n") {
                  it.partOfTotalIndexingTimeOfThisFileType.presentablePercentages() + ": " +
                  it.providerName + " " +
                  it.numberOfFiles + " files of total size " +
                  it.totalFilesSize.presentableSize()
                }
              )
            }
          }
        }
      }

      h1("Statistics per indexer")
      table {
        thead {
          tr {
            th("Index")
            th("Number of files")
            th("Part of total indexing time")
            th("Total number of files indexed by extensions")
            th("Total files size")
            th("Indexing speed")
          }
        }
        tbody {
          for (statsPerIndexer in projectIndexingHistory.totalStatsPerIndexer) {
            tr {
              td(statsPerIndexer.indexId)
              td(statsPerIndexer.totalNumberOfFiles.toString())
              td(statsPerIndexer.partOfTotalIndexingTime.presentablePercentages())
              td(statsPerIndexer.totalNumberOfFilesIndexedByExtensions.toString())
              td(statsPerIndexer.totalFilesSize.presentableSize())
              td(statsPerIndexer.indexingSpeed.presentableSpeed())
            }
          }
        }
      }

      h1("Scanning")
      table {
        thead {
          tr {
            th("Provider name")
            th("Number of scanned files")
            th("Number of skipped files, which are already iterated for a different entity")
            th("Number of files scheduled for indexing")
            th("Number of up-to-date files")
            th("Number of files indexed by infrastructure extensions")
            th("Scanning time")
            th("Time processing up-to-date files")
            th("Time updating content-less indexes")
            th("Time indexing without content")
          }
        }
        tbody {
          for (scanningStats in projectIndexingHistory.scanningStatistics) {
            tr {
              td(scanningStats.providerName)
              td(scanningStats.numberOfScannedFiles.toString())
              td(scanningStats.numberOfSkippedFiles.toString())
              td(scanningStats.numberOfFilesForIndexing.toString())
              td(scanningStats.numberOfUpToDateFiles.toString())
              td(scanningStats.numberOfFilesFullyIndexedByInfrastructureExtensions.toString())
              td(scanningStats.scanningTime.presentableDuration())
              td(scanningStats.timeProcessingUpToDateFiles.presentableDuration())
              td(scanningStats.timeUpdatingContentLessIndexes.presentableDuration())
              td(scanningStats.timeIndexingWithoutContent.presentableDuration())
            }
          }
        }
      }

      h1("Indexing with content")
      table {
        thead {
          tr {
            th("Provider name")
            th("Total indexing time")
            th("Number of indexed files")
            th("Number of files indexed by infrastructure extensions")
            th("Number of too large for indexing files")
            th("Files")
          }
        }
        tbody {
          for (providerStats in projectIndexingHistory.fileProviderStatistics) {
            tr {
              td(providerStats.providerName)
              td(providerStats.totalIndexingTime.presentableDuration())
              td(providerStats.totalNumberOfFiles.toString())
              td(providerStats.totalNumberOfFilesFullyIndexedByExtensions.toString())
              td(providerStats.numberOfTooLargeForIndexingFiles.toString())
              val indexedFiles = providerStats.indexedFiles
              if (indexedFiles != null) {
                td(indexedFiles.joinToString("\n") { it.presentablePath })
              }
              else {
                td("<unknown>")
              }
            }
          }
        }
      }
    }
  }.toString()
}

private fun createTag(body: HtmlBuilder.() -> Unit, tag: Element): Element {
  val tagBuilder = HtmlBuilder()
  tagBuilder.body()
  return tagBuilder.toFragment().wrapWith(tag)
}

private fun HtmlBuilder.text(@Nls text: String) {
  append(text)
}

private infix operator fun HtmlBuilder.plus(@Nls text: String) = text(text)
private fun HtmlBuilder.h1(@Nls title: String) = append(HtmlChunk.text(title).wrapWith(tag("h1")))

private fun HtmlBuilder.table(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("table")))
private fun HtmlBuilder.thead(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("thead")))
private fun HtmlBuilder.tbody(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("tbody")))
private fun HtmlBuilder.tr(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("tr")))
private fun HtmlBuilder.th(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("th")))
private fun HtmlBuilder.th(@Nls text: String) = th { text(text) }
private fun HtmlBuilder.td(body: HtmlBuilder.() -> Unit) = append(createTag(body, tag("td")))
private fun HtmlBuilder.td(@Nls text: String) = td { text(text) }

private fun HtmlBuilder.div(body: HtmlBuilder.() -> Unit) = append(createTag(body, div()))
private fun HtmlBuilder.body(body: HtmlBuilder.() -> Unit) = append(createTag(body, HtmlChunk.body()))
private fun HtmlBuilder.html(body: HtmlBuilder.() -> Unit) = createTag(body, HtmlChunk.html())
private fun html(body: HtmlBuilder.() -> Unit) = HtmlBuilder().html(body)