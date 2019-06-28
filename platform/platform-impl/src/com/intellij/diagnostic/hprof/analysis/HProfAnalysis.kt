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

import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.util.FileBackedIntList
import com.intellij.diagnostic.hprof.util.PartialProgressIndicator
import com.intellij.diagnostic.hprof.visitors.RemapIDsVisitor
import com.google.common.base.Stopwatch
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.TestOnly
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class HProfAnalysis(private val hprofFileChannel: FileChannel,
                    private val tempFilenameSupplier: TempFilenameSupplier) {

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

  @TestOnly
  fun setIncludeMetaInfo(value: Boolean) {
    includeMetaInfo = value
  }

  private fun openTempEmptyFileChannel(type: String): FileChannel {
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

  fun analyze(progress: ProgressIndicator): String {
    val result = StringBuilder()
    val totalStopwatch = Stopwatch.createStarted()
    val stopwatch = Stopwatch.createStarted()

    progress.text = "Analyze Heap"
    progress.text2 = "Open heap file"
    progress.fraction = 0.0

    val parser = HProfEventBasedParser(hprofFileChannel)
    try {
      progress.text2 = "Create class definition map"
      progress.fraction = 0.0

      var classStore = ClassStore.create(parser)

      progress.text2 = "Create class histogram"
      progress.fraction = 0.1

      val histogram = Histogram.create(parser, classStore)

      val nominatedClasses = ClassNomination(histogram, 5).nominateClasses()

      progress.text2 = "Create id mapping file"
      progress.fraction = 0.2

      // Currently, there is a maximum count of supported instances. Produce simplified report
      // (histogram only), if the count exceeds maximum.
      if (!isSupported(histogram.instanceCount)) {
        result.appendln("Histogram. Top 50 by instance count:")
        result.appendln(histogram.prepareReport("All", 50))
        return result.toString()
      }

      val idMappingChannel = openTempEmptyFileChannel("id-mapping")
      val remapIDsVisitor = RemapIDsVisitor.createFileBased(
        idMappingChannel,
        histogram.instanceCount)

      parser.accept(remapIDsVisitor, "id mapping")
      parser.setIdRemappingFunction(remapIDsVisitor.getRemappingFunction())
      classStore = classStore.createStoreWithRemappedIDs(remapIDsVisitor.getRemappingFunction())

      progress.text2 = "Create object graph files"
      progress.fraction = 0.3

      val navigator = ObjectNavigator.createOnAuxiliaryFiles(
        parser,
        openTempEmptyFileChannel("auxOffset"),
        openTempEmptyFileChannel("aux"),
        classStore,
        histogram.instanceCount
      )

      if (includeMetaInfo) {
        result.appendln("Prepare files duration: $stopwatch")
      }
      stopwatch.reset().start()

      val parentList = FileBackedIntList.createEmpty(openTempEmptyFileChannel("parents"), navigator.instanceCount + 1)
      val sizesList = FileBackedIntList.createEmpty(openTempEmptyFileChannel("sizes"), navigator.instanceCount + 1)
      val visitedList = FileBackedIntList.createEmpty(openTempEmptyFileChannel("visited"), navigator.instanceCount + 1)

      val nominatedClassNames = nominatedClasses.map { it.classDefinition.name }.toSet()
      val analyzeGraph = AnalyzeGraph(navigator,
                                      parentList,
                                      sizesList,
                                      visitedList,
                                      nominatedClassNames,
                                      includeMetaInfo
      )
      val analyzeReport = analyzeGraph.analyze(PartialProgressIndicator(progress, 0.4, 0.4))
      val analyzeDisposer = AnalyzeDisposer(navigator)
      val disposedObjectsIDsSet = analyzeDisposer.computeDisposedObjectsIDsSet(parentList)

      val graphReport = analyzeGraph.prepareReport(PartialProgressIndicator(progress, 0.8, 0.2),
                                                   disposedObjectsIDsSet)
      val strongRefHistogram = analyzeGraph.getStrongRefHistogram()

      val disposerTreeReport = analyzeDisposer.createDisposerTreeReport()
      val disposerDisposedObjectsReport = analyzeDisposer.analyzeDisposedObjects(disposedObjectsIDsSet, parentList, sizesList)

      if (includeMetaInfo) {
        result.appendln("Analysis duration: $stopwatch")
        result.appendln("TOTAL DURATION: $totalStopwatch")
        result.appendln("Temp files:")
        result.appendln("  heapdump = ${hprofFileChannel.size() / 1_000_000}MB")

        tempFiles.forEach { temp ->
          val channel = temp.channel
          if (channel.isOpen) {
            result.appendln("  ${temp.type} = ${channel.size() / 1_000_000}MB")
          }
        }
      }
      result.appendln("Histogram. Top 50 by instance count [All-objects] [Only-strong-ref]:")
      result.append(
        Histogram.prepareMergedHistogramReport(histogram, "All", strongRefHistogram, "Strong-ref", 50))
      result.appendln()
      result.appendln("Nominated classes:")
      nominatedClasses.sortedBy { it.classDefinition.name }.forEach {
        result.appendln(" --> ${it.totalInstances} ${it.classDefinition.prettyName} (${it.totalBytes / 1_000_000}MB)")
      }
      result.appendln()

      result.appendln("=============== OBJECT GRAPH ===============")
      result.append(analyzeReport)
      result.append(graphReport)
      result.appendln("============== DISPOSER TREE ===============")
      result.append(disposerTreeReport)
      result.appendln("============= DISPOSED OBJECTS =============")
      result.append(disposerDisposedObjectsReport)
    }
    finally {
      parser.close()
      closeAndDeleteTemporaryFiles()
    }
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
