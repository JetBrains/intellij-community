// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.logs.MLSessionLoggerBuilder
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.session.*
import kotlinx.coroutines.launch

internal class AnalysisLogger<M : MLModel<P>, P : Any>(
  private val sessionAnalyserProvider: SessionAnalyserProvider<M, P>,
  private val structureAnalyser: StructureAnalyser<M, P>,
  private val sessionLoggerBuilder: MLSessionLoggerBuilder<P>,
) : MLApproachInitializationListener<M, P> {

  override fun onAttemptedToStartSession(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, callParameters: Environment): MLApproachListener<M, P> {
    val mlSessionLogger = sessionLoggerBuilder.startLoggingSession()
    val sessionAnalyser: SessionAnalyser<M, P> = sessionAnalyserProvider.startSessionAnalysis(callParameters, permanentSessionEnvironment)
    apiPlatform.coroutineScope.launch {
      mlSessionLogger.logBeforeSessionStarted(sessionAnalyser.onBeforeSessionStarted())
    }
    return object : MLApproachListener<M, P> {
      override fun onFailedToStartSessionWithException(exception: Throwable) {
        apiPlatform.coroutineScope.launch {
          mlSessionLogger.logSessionException(sessionAnalyser.onSessionFailedWithException(exception))
        }
      }

      override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {
        apiPlatform.coroutineScope.launch {
          mlSessionLogger.logStartFailure(sessionAnalyser.onSessionFailedToStart(failure))
        }
      }

      override fun onStartedSession(session: Session<P>, mlModel: M): MLSessionListener<M, P> {
        apiPlatform.coroutineScope.launch {
          mlSessionLogger.logStarted(sessionAnalyser.onSessionStarted(session, mlModel))
        }

        return object : MLSessionListener<M, P> {
          override fun onSessionDescriptionFinished(sessionTree: DescribedRootContainer<M, P>) {
            apiPlatform.coroutineScope.launch {
              val structureAnalysis = structureAnalyser.analyse(sessionTree)
              val analysedSessionTree = buildAnalysedSessionTree(sessionTree, structureAnalysis) as AnalysedRootContainer<P>
              val sessionAnalysis = sessionAnalyser.onSessionFinished(sessionTree)
              mlSessionLogger.logFinished(analysedSessionTree, sessionAnalysis)
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
