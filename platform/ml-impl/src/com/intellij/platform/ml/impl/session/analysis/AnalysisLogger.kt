// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.logs.MLSessionLogger
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.session.*
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class AnalysisLogger<M : MLModel<P>, P : Any>(
  private val sessionAnalyserProvider: SessionAnalyserProvider<M, P>,
  private val structureAnalyser: StructureAnalyser<M, P>,
  private val baseSessionLogger: MLSessionLogger<P>,
) : MLApproachInitializationListener<M, P> {

  override fun onAttemptedToStartSession(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, callParameters: Environment): MLApproachListener<M, P> {
    val mlSessionLogger = PatchingLogger(baseSessionLogger)

    val sessionAnalyser: SessionAnalyser<M, P>? = sessionAnalyserProvider.startSessionAnalysis(callParameters, permanentSessionEnvironment)

    val patchOnBeforeStarted = mlSessionLogger.acquirePatchLogger(false)
    apiPlatform.coroutineScope.launch {
      sessionAnalyser?.onBeforeSessionStarted()?.let { patchOnBeforeStarted.logSession(it) }
    }

    return object : MLApproachListener<M, P> {
      override fun onFailedToStartSessionWithException(exception: Throwable) {
        val sessionPatch = mlSessionLogger.acquirePatchLogger(true)
        apiPlatform.coroutineScope.launch {
          val analysis = sessionAnalyser?.onSessionFailedWithException(exception) ?: emptyList()
          sessionPatch.logSession(analysis)
        }
      }

      override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {
        val sessionPatch = mlSessionLogger.acquirePatchLogger(true)
        apiPlatform.coroutineScope.launch {
          val analysis = sessionAnalyser?.onSessionFailedToStart(failure) ?: emptyList()
          sessionPatch.logSession(analysis)
        }
      }

      override fun onStartedSession(session: Session<P>, mlModel: M): MLSessionListener<M, P> {
        val sessionPatchStarted = mlSessionLogger.acquirePatchLogger(false)
        apiPlatform.coroutineScope.launch {
          val analysis = sessionAnalyser?.onSessionStarted(session, mlModel) ?: emptyList()
          sessionPatchStarted.logSession(analysis)
        }

        val sessionPatchFinished = mlSessionLogger.acquirePatchLogger(false)
        val sessionPatchStructure = mlSessionLogger.acquirePatchLogger(true)

        return object : MLSessionListener<M, P> {
          override fun onSessionFinishedSuccessfully(sessionTree: DescribedRootContainer<M, P>) {
            apiPlatform.coroutineScope.launch {
              val structureAnalysis = structureAnalyser.analyse(sessionTree)
              val analysedSessionTree = buildAnalysedSessionTree(sessionTree, structureAnalysis) as AnalysedRootContainer<P>
              sessionPatchFinished.logSession(sessionAnalyser?.onSessionFinished(sessionTree) ?: emptyList())
              sessionPatchStructure.logStructure(analysedSessionTree)
            }
          }
        }
      }
    }
  }

  private fun buildAnalysedSessionTree(tree: DescribedSessionTree<M, P>, structureAnalysis: Map<DescribedSessionTree<M, P>, PerTier<Set<Feature>>>): AnalysedSessionTree<P> {
    val treeAnalysisPerInstance: PerTierInstance<AnalysedTierData> = tree.levelData.mainInstances.entries.associate { (tierInstance, data) ->
      tierInstance to AnalysedTierData(data.description, structureAnalysis[tree]?.get(tierInstance.tier) ?: emptySet())
    }

    val analysedLevel = AnalysedLevel(mainInstances = treeAnalysisPerInstance, additionalInstances = tree.levelData.additionalInstances, callParameters = tree.levelData.callParameters)

    return when (tree) {
      is SessionTree.Branching<M, DescribedTierData, P> -> {
        SessionTree.Branching(analysedLevel, tree.children.map { buildAnalysedSessionTree(it, structureAnalysis) })
      }
      is SessionTree.Leaf<M, DescribedTierData, P> -> {
        SessionTree.Leaf(analysedLevel, tree.prediction)
      }
      is SessionTree.ComplexRoot -> {
        SessionTree.ComplexRoot(Unit, analysedLevel, tree.children.map { buildAnalysedSessionTree(it, structureAnalysis) })
      }
      is SessionTree.SolitaryLeaf -> {
        SessionTree.SolitaryLeaf(Unit, analysedLevel, tree.prediction)
      }
    }
  }
}

private class PatchingLogger<P : Any>(
  private val baseMLSessionLogger: MLSessionLogger<P>
) {
  private val partialSessionLogs: MutableList<List<EventPair<*>>> = mutableListOf()
  private val logLock = ReentrantLock()

  private var structure: AnalysedRootContainer<P>? = null
  private var flushStoppers = 1
  private var alreadyAcquiredLastPatch = false
  private var isAlreadyLogged = false

  inner class MLSessionPatchLogger(private val onAfterPatchLogged: () -> Unit) {
    private var patchAlreadyLogged = false

    fun logSession(session: List<EventPair<*>>) = logLock.withLock {
      require(!patchAlreadyLogged)
      partialSessionLogs += session
      patchAlreadyLogged = true
      onAfterPatchLogged()
    }

    fun logStructure(structure: AnalysedRootContainer<P>) = logLock.withLock {
      require(!patchAlreadyLogged)
      this@PatchingLogger.structure = structure
      patchAlreadyLogged = true
      onAfterPatchLogged()
    }
  }

  fun acquirePatchLogger(isLastPatch: Boolean): MLSessionPatchLogger = logLock.withLock {
    if (isLastPatch) {
      require(!alreadyAcquiredLastPatch)
      alreadyAcquiredLastPatch = true
    }

    flushStoppers += 1
    return MLSessionPatchLogger {
      removeStopper()
      if (isLastPatch) {
        removeStopper()
      }
    }
  }

  private fun removeStopper() {
    flushStoppers -= 1
    if (flushStoppers == 0) {
      assert(!isAlreadyLogged)
      isAlreadyLogged = true
      baseMLSessionLogger.logSession(partialSessionLogs.flatten(), structure)
    }
  }
}
