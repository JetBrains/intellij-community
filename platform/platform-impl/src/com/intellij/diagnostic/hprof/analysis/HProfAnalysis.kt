/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.analysis

import com.google.common.base.Stopwatch
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.hprof.classstore.HProfMetadata
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.util.*
import com.intellij.diagnostic.hprof.util.HeapReportUtils.sectionHeader
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsCount
import com.intellij.diagnostic.hprof.visitors.RemapIDsVisitor
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.NonNls
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class HProfAnalysis(private val hprofFileChannel: FileChannel,
                    private val tempFilenameSupplier: TempFilenameSupplier,
                    private val analysisCallback: (AnalysisContext, ListProvider, ProgressIndicator) -> String) {

  interface TempFilenameSupplier {
    fun getTempFilePath(type: String): Path
  }

  private data class TempFile(
    val type: String,
    val path: Path,
    val channel: FileChannel
  )

  private val tempFiles = mutableListOf<TempFile>()

  private var includeMetaInfo = true

  fun setIncludeMetaInfo(value: Boolean) {
    includeMetaInfo = value
  }

  var onlyStrongReferences: Boolean = false
  var includeClassesAsRoots: Boolean = true

  private fun openTempEmptyFileChannel(@NonNls type: String): FileChannel {
    val tempPath = tempFilenameSupplier.getTempFilePath(type)
    val tempChannel = FileChannel.open(tempPath,
                                       StandardOpenOption.READ,
                                       StandardOpenOption.WRITE,
                                       StandardOpenOption.CREATE,
                                       StandardOpenOption.TRUNCATE_EXISTING,
                                       StandardOpenOption.DELETE_ON_CLOSE)

    tempFiles.add(TempFile(type, tempPath, tempChannel))
    return tempChannel
  }

  private val fileBackedListProvider = object: ListProvider {
    override fun createUByteList(name: String, size: Long) = FileBackedUByteList.createEmpty(openTempEmptyFileChannel(name), size)
    override fun createUShortList(name: String, size: Long) = FileBackedUShortList.createEmpty(openTempEmptyFileChannel(name), size)
    override fun createIntList(name: String, size: Long) = FileBackedIntList.createEmpty(openTempEmptyFileChannel(name), size)
  }

  fun analyze(progress: ProgressIndicator): String {
    val result = StringBuilder()
    val totalStopwatch = Stopwatch.createStarted()
    val prepareFilesStopwatch = Stopwatch.createStarted()
    val analysisStopwatch = Stopwatch.createUnstarted()

    progress.text = DiagnosticBundle.message("hprof.analysis.progress.text.analyze.heap")
    progress.text2 = DiagnosticBundle.message("hprof.analysis.progress.details.open.heap.file")
    progress.fraction = 0.0

    val parser = HProfEventBasedParser(hprofFileChannel)
    try {
      progress.text2 = DiagnosticBundle.message("hprof.analysis.progress.details.create.class.definition.map")
      progress.fraction = 0.0

      val hprofMetadata = HProfMetadata.create(parser)

      progress.text2 = DiagnosticBundle.message("hprof.analysis.progress.details.create.class.histogram")
      progress.fraction = 0.1

      val histogram = Histogram.create(parser, hprofMetadata.classStore)

      val nominatedClasses = ClassNomination(histogram, 10).nominateClasses()

      progress.text2 = DiagnosticBundle.message("hprof.analysis.progress.details.create.id.mapping.file")
      progress.fraction = 0.2

      // Currently, there is a maximum count of supported instances. Produce simplified report
      // (histogram only), if the count exceeds maximum.
      if (!isSupported(histogram.instanceCount)) {
        return prepareSimplifiedReport(result, histogram)
      }

      val idMappingChannel = openTempEmptyFileChannel("id-mapping")
      val remapIDsVisitor = RemapIDsVisitor.createFileBased(
        idMappingChannel,
        histogram.instanceCount)

      parser.accept(remapIDsVisitor, "id mapping")
      val idMapper = remapIDsVisitor.getIDMapper()
      parser.setIDMapper(idMapper)
      hprofMetadata.remapIds(idMapper)

      progress.text2 = DiagnosticBundle.message("hprof.analysis.progress.details.create.object.graph.files")
      progress.fraction = 0.3

      val navigator = try {
        ObjectNavigator.createOnAuxiliaryFiles(
          parser,
          openTempEmptyFileChannel("auxOffset"),
          openTempEmptyFileChannel("aux"),
          hprofMetadata,
          histogram.instanceCount
        )
      }
      catch (e: IllegalArgumentException) {
        // Aux channels can exceed 2GB despite the relatively small number
        // of supported instances.
        // It's possible to use a buffer with a sliding window,
        // but there are certain complications, so let's keep
        // it simplified for the time being
        return prepareSimplifiedReport(result, histogram)
      }

      prepareFilesStopwatch.stop()

      val parentList = fileBackedListProvider.createIntList("parents", navigator.instanceCount + 1)
      val sizesList = fileBackedListProvider.createIntList("sizes", navigator.instanceCount + 1)
      val visitedList = fileBackedListProvider.createIntList("visited", navigator.instanceCount + 1)
      val refIndexList = fileBackedListProvider.createUByteList("refIndex", navigator.instanceCount + 1)

      analysisStopwatch.start()

      val nominatedClassNames = nominatedClasses.map { it.classDefinition.name }
      val analysisConfig = AnalysisConfig(perClassOptions = AnalysisConfig.PerClassOptions(classNames = nominatedClassNames
                                                                                                        + listOf("com.intellij.openapi.editor.impl.EditorImpl")
                                                                                                        + listOf("com.intellij.openapi.project.impl.ProjectImpl")),
                                          metaInfoOptions = AnalysisConfig.MetaInfoOptions(include = includeMetaInfo),
                                          traverseOptions = AnalysisConfig.TraverseOptions(onlyStrongReferences = onlyStrongReferences, includeClassesAsRoots = includeClassesAsRoots))
      val analysisContext = AnalysisContext(
        navigator,
        analysisConfig,
        parentList,
        sizesList,
        visitedList,
        refIndexList,
        histogram
      )

      val analysisReport = analysisCallback(analysisContext, fileBackedListProvider, PartialProgressIndicator(progress, 0.4, 0.4))
      if (analysisReport.isNotBlank()) {
        result.appendLine(analysisReport)
      }

      analysisStopwatch.stop()

      if (includeMetaInfo) {
        result.appendLine(sectionHeader("Analysis information"))
        result.appendLine("Prepare files duration: $prepareFilesStopwatch")
        result.appendLine("Analysis duration: $analysisStopwatch")
        result.appendLine("TOTAL DURATION: $totalStopwatch")
        result.appendLine("Temp files:")
        result.appendLine("  heapdump = ${toShortStringAsCount(hprofFileChannel.size())}")

        tempFiles.forEach { temp ->
          val channel = temp.channel
          if (channel.isOpen) {
            result.appendLine("  ${temp.type} = ${toShortStringAsCount(channel.size())}")
          }
        }
      }
    }
    finally {
      parser.close()
      closeAndDeleteTemporaryFiles()
    }
    return result.toString()
  }

  private fun prepareSimplifiedReport(result: StringBuilder, histogram: Histogram): String {
    result.appendLine(histogram.prepareReport("All", 50))
    return result.toString()
  }

  private fun isSupported(instanceCount: Long): Boolean {
    // Limitation due to FileBackedHashMap in RemapIDsVisitor. Many other components
    // assume instanceCount <= Int.MAX_VALUE.
    return RemapIDsVisitor.isSupported(instanceCount) && instanceCount <= Int.MAX_VALUE
  }

  private fun closeAndDeleteTemporaryFiles() {
    tempFiles.forEach { tempFile ->
      try {
        tempFile.channel.close()
      }
      catch (ignored: Throwable) {
      }
      try {
        tempFile.path.let { Files.deleteIfExists(it) }
      }
      catch (ignored: Throwable) {
      }
    }
    tempFiles.clear()
  }
}
