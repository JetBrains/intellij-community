// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl

import com.intellij.framework.detection.DetectedFrameworkDescription
import com.intellij.framework.detection.DetectionExcludesConfiguration
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import kotlin.time.Duration.Companion.milliseconds

private val LOG: Logger = Logger.getInstance(FrameworkDetectorQueue::class.java)

@OptIn(FlowPreview::class)
internal class FrameworkDetectorQueue(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) {
  lateinit var detectedFrameworksData: DetectedFrameworksData

  @Volatile
  private var isSuspended: Boolean = false

  @Volatile
  var notificationListener: Consumer<Collection<String>>? = null

  private val frameworkRequests = MutableSharedFlow<Set<String>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scheduleFlow()
  }

  private fun scheduleFlow() {
    coroutineScope.launch {
      frameworkRequests
        .debounce(500.milliseconds)
        .collectLatest { detectors ->
          doRunDetection(detectors)
        }
    }
  }

  fun queueDetection(detectors: Set<String>) {
    if (isSuspended) return

    frameworkRequests.tryEmit(LinkedHashSet(detectors))
  }

  fun suspend() {
    isSuspended = true

    coroutineScope.coroutineContext.cancelChildren()
  }

  fun resume(detectors: Set<String>) {
    isSuspended = false

    scheduleFlow()
    queueDetection(detectors)
  }

  @RequiresReadLock
  fun runDetector(detectorId: String, processNewFilesOnly: Boolean): List<DetectedFrameworkDescription> {
    val acceptedFiles = FileBasedIndex.getInstance().getContainingFiles(FrameworkDetectionIndex.NAME, detectorId,
                                                                        GlobalSearchScope.projectScope(project))
    val filesToProcess: Collection<VirtualFile> = if (processNewFilesOnly) {
      detectedFrameworksData.retainNewFiles(detectorId, acceptedFiles)
    }
    else {
      ArrayList(acceptedFiles)
    }
    val detector = FrameworkDetectorRegistry.getInstance().getDetectorById(detectorId)
    if (detector == null) {
      LOG.info("Framework detector not found by id $detectorId")
      return emptyList()
    }

    val excludesConfiguration = DetectionExcludesConfiguration.getInstance(project) as DetectionExcludesConfigurationImpl
    excludesConfiguration.removeExcluded(filesToProcess, detector.frameworkType)
    if (LOG.isDebugEnabled) {
      LOG.debug("Detector '${detector.detectorId}': ${acceptedFiles.size} accepted files, ${filesToProcess.size} files to process")
    }
    val frameworks: List<DetectedFrameworkDescription> = if (!filesToProcess.isEmpty()) {
      detector.detect(filesToProcess, FrameworkDetectionContextImpl(project))
    }
    else {
      emptyList()
    }
    return frameworks
  }

  @TestOnly
  fun testRunDetection(detectors: Collection<String>) {
    runBlockingMaybeCancellable {
      doRunDetection(detectors)
    }
  }

  private suspend fun doRunDetection(detectors: Collection<String>) {
    if (LightEdit.owns(project)) {
      return
    }

    val newDescriptions = mutableListOf<DetectedFrameworkDescription>()
    val oldDescriptions = mutableListOf<DetectedFrameworkDescription>()

    if (LOG.isDebugEnabled) {
      LOG.debug("Starting framework detectors: $detectors")
    }

    for (detectorId in detectors) {
      val frameworks = smartReadAction(project) {
        runDetector(detectorId, true)
      }

      oldDescriptions.addAll(frameworks)
      val updated = detectedFrameworksData.updateFrameworksList(detectorId, frameworks)
      newDescriptions.addAll(updated)
      oldDescriptions.removeAll(updated)

      if (LOG.isDebugEnabled) {
        LOG.debug("${frameworks.size} frameworks detected, ${updated.size} changed")
      }
    }

    if (!newDescriptions.isEmpty()) {
      val newNamesToNotify = readAction {
        val names: MutableSet<String> = HashSet()
        for (description in FrameworkDetectionUtil.removeDisabled(newDescriptions, oldDescriptions)) {
          names.add(description.detector.frameworkType.presentableName)
        }
        names
      }

      notificationListener?.accept(newNamesToNotify)
    }
  }
}