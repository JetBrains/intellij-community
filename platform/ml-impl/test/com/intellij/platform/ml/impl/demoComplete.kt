// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.*
import com.intellij.platform.ml.analysis.SessionAnalyser
import com.intellij.platform.ml.analysis.StructureAnalyser
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.environment.get
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureFilter
import com.intellij.platform.ml.feature.FeatureSelector
import com.intellij.platform.ml.impl.logs.LanguageSpecific
import com.intellij.platform.ml.impl.logs.Versioned
import com.intellij.platform.ml.logs.AnalysisMethods
import com.intellij.platform.ml.logs.EntireSessionLoggingStrategy
import com.intellij.platform.ml.logs.NO_DESCRIPTION
import com.intellij.platform.ml.logs.schema.BooleanEventField
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import com.intellij.platform.ml.logs.schema.IntEventField
import com.intellij.platform.ml.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.monitoring.MLApproachListener
import com.intellij.platform.ml.monitoring.MLSessionListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy
import com.intellij.platform.ml.session.*
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.function.Consumer
import kotlin.random.Random

class RandomModel(val seed: Int) : MLModel<Double>, Versioned, LanguageSpecific {
  private val generator = Random(seed)

  object Provider : MLModel.Provider<RandomModel, Double> {
    private val generator = Random(1)
    override fun provideModel(callParameters: Environment, environment: Environment, sessionTiers: List<LevelTiers>): RandomModel? {
      return if (generator.nextBoolean()) {
        if (generator.nextBoolean()) RandomModel(generator.nextInt()) else throw IllegalStateException("A random error was encountered")
      }
      else null
    }
  }

  override val knownFeatures: PerTier<FeatureSelector> = mapOf(
    TierCompletionSession to FeatureSelector.EVERYTHING,
    TierLookup to FeatureSelector.EVERYTHING,
    TierGit to FeatureSelector.EVERYTHING,
    TierItem to FeatureSelector.EVERYTHING,
  )

  override fun predict(callParameters: List<Environment>, features: PerTier<Set<Feature>>): Double {
    return generator.nextDouble()
  }

  override val language: Language = PlainTextLanguage.INSTANCE

  override val version: Version = Version(0, 0, 1)
}

private class MockTaskFusLogger : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("mock-task", 1).also {
      it.registerMLTaskLogging("finished", MockTask, EntireSessionLoggingStrategy.DOUBLE,
                               AnalysisMethods(
                                 sessionAnalysers = listOf(FailureLogger(),
                                                           ExceptionLogger(),
                                                           RandomModelSeedAnalyser,
                                                           VeryUselessSessionAnalyser()),
                                 structureAnalysers = listOf(SomeStructureAnalyser())
                               ))
    }
  }

  override fun getGroup() = GROUP
}

private object ThisTestApiPlatform : TestApiPlatform() {
  override val tierDescriptors = listOf(
    CompletionSessionFeatures(),
    ItemFeatures1(),
    GitFeatures1(),
  )

  override val environmentExtenders = listOf(
    GitInformant(),
  )

  override val taskApproaches: List<MLTaskApproachBuilder<*>> = listOf(
    MockTaskApproachBuilder()
  )

  override val initialTaskListeners: List<MLTaskGroupListener> = listOf(
    SomeListener("Nika"),
    SomeListener("Alex"),
  )
}

object MockTask : MLTask<Double>(
  name = "mock",
  predictionClass = Double::class.java,
  levels = listOf(
    setOf(TierCompletionSession),
    setOf(TierLookup),
    setOf(TierItem)
  ),
  callParameters = listOf(
    setOf(),
    setOf(),
    setOf()
  )
)

internal class SomeStructureAnalyser<M : MLModel<Double>> : StructureAnalyser<M, Double> {
  companion object {
    val SESSION_IS_GOOD = FeatureDeclaration.boolean("very_good_session")
    val LOOKUP_INDEX = FeatureDeclaration.int("lookup_index")
  }

  override suspend fun analyse(sessionTreeRoot: DescribedRootContainer<M, Double>): Map<DescribedSessionTree<M, Double>, PerTier<Set<Feature>>> {
    val analysis = mutableMapOf<DescribedSessionTree<M, Double>, PerTier<Set<Feature>>>()
    sessionTreeRoot.accept(LookupAnalyser(analysis))
    analysis[sessionTreeRoot] = mapOf(
      TierCompletionSession to setOf(SESSION_IS_GOOD with true)
    )

    // Pretend that analysis is taking some long time
    delay(1000)

    return analysis
  }

  private class LookupAnalyser<M : MLModel<Double>>(
    private val analysis: MutableMap<DescribedSessionTree<M, Double>, PerTier<Set<Feature>>>
  ) : SessionTree.LevelVisitor<M, DescribedTierData, Double>(levelIndex = 1) {
    override fun visitLevel(level: LevelData<DescribedTierData>, levelRoot: SessionTree<M, DescribedTierData, Double>) {
      val lookup = level.environment[TierLookup]
      analysis[levelRoot] = mapOf(TierLookup to setOf(LOOKUP_INDEX with lookup.index))
    }
  }

  override val declaration: PerTier<Set<FeatureDeclaration<*>>>
    get() = mapOf(
      TierCompletionSession to setOf(SESSION_IS_GOOD),
      TierLookup to setOf(LOOKUP_INDEX)
    )
}

object RandomModelSeedAnalyser : SessionAnalyser.Default<RandomModel, Double>() {
  private val SEED = IntEventField("random_seed", NO_DESCRIPTION)

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<Double>, mlModel: RandomModel): List<EventPair<*>> {
    return listOf(SEED with mlModel.seed)
  }

  override val declaration: List<EventField<*>> = listOf(SEED)
}

class SomeListener(private val name: String) : MLTaskGroupListener {
  override val approachListeners = listOf(
    MockTaskApproachBuilder::class.java monitoredBy InitializationListener()
  )

  private fun log(message: String) = println("[Listener $name says] $message")

  inner class InitializationListener : MLApproachInitializationListener<RandomModel, Double> {
    override fun onAttemptedToStartSession(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment): MLApproachListener<RandomModel, Double> {
      log("attempted to initialize session")
      return ApproachListener()
    }
  }

  inner class ApproachListener : MLApproachListener<RandomModel, Double> {
    override fun onFailedToStartSessionWithException(exception: Throwable) {
      log("failed to start session with exception: $exception, trace: ${exception.stackTraceToString()}")
    }

    override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<Double>) {
      log("failed to start session with outcome: $failure")
    }

    override fun onStartedSession(session: Session<Double>, mlModel: RandomModel): MLSessionListener<RandomModel, Double> {
      log("session was started successfully: $session")
      return SessionListener()
    }
  }

  inner class SessionListener : MLSessionListener<RandomModel, Double> {
    override fun onSessionFinishedSuccessfully(sessionTree: DescribedRootContainer<RandomModel, Double>) {
      log("session successfully described: $sessionTree")
    }
  }
}

class MockTaskApproachDetails : LogDrivenModelInference.SessionDetails<RandomModel, Double> {
  override val additionallyDescribedTiers: List<Set<Tier<*>>> = listOf(
    setOf(TierGit),
    setOf(),
    setOf(),
  )

  override val mlModelProvider = RandomModel.Provider

  override fun getNotUsedDescription(callParameters: Environment, mlModel: RandomModel) = mapOf(
    TierCompletionSession to FeatureFilter.REJECT_ALL,
    TierLookup to FeatureFilter.REJECT_ALL,
    TierItem to FeatureFilter.REJECT_ALL,
    TierGit to FeatureFilter.REJECT_ALL
  )

  override val descriptionComputer: DescriptionComputer = StateFreeDescriptionComputer

  class Builder : LogDrivenModelInference.SessionDetails.Builder<RandomModel, Double> {
    override fun build(apiPlatform: MLApiPlatform): LogDrivenModelInference.SessionDetails<RandomModel, Double> {
      return MockTaskApproachDetails()
    }
  }
}

private class MockTaskApproachBuilder : LogDrivenModelInference.Builder<RandomModel, Double>(MockTask, MockTaskApproachDetails.Builder())


private class VeryUselessSessionAnalyser : SessionAnalyser.Default<RandomModel, Double>() {
  companion object {
    val ON_BEFORE_STARTED = BooleanEventField("on_before_started", NO_DESCRIPTION)
    val ON_SESSION_STARTED = BooleanEventField("on_session_started", NO_DESCRIPTION)
    val ON_SESSION_FINISHED = BooleanEventField("on_session_finished", NO_DESCRIPTION)
  }

  override val declaration: List<EventField<*>> = listOf(
    ON_BEFORE_STARTED, ON_SESSION_STARTED, ON_SESSION_FINISHED
  )

  override suspend fun onBeforeSessionStarted(callParameters: Environment, sessionEnvironment: Environment): List<EventPair<*>> {
    return listOf(ON_BEFORE_STARTED with true)
  }

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<Double>, mlModel: RandomModel): List<EventPair<*>> {
    return listOf(ON_SESSION_STARTED with true)
  }

  override suspend fun onSessionFinished(callParameters: Environment, sessionEnvironment: Environment, sessionTreeRoot: DescribedRootContainer<RandomModel, Double>): List<EventPair<*>> {
    return listOf(ON_SESSION_FINISHED with true)
  }
}

class MockTaskTaskTest : MLApiLogsTestCase() {
  fun `test demo ml task`() {
    // After the session is finished, it will be logged to community/platform/ml-impl/testResources/ml_logs.js

    val logs: MutableList<Pair<String, Map<String, Any>>> = mutableListOf()
    val collectLogs = Consumer { fusLog: LogEvent ->
      logs.add(fusLog.event.id to fusLog.event.data)
    }

    ReplaceableIJPlatform.replacingWith(ThisTestApiPlatform) {
      registerEventLogger(MockTaskFusLogger())

      FUCollectorTestCase.listenForEvents("FUS", this.testRootDisposable, collectLogs) {

        repeat(3) { sessionIndex ->

          println("Demo session #$sessionIndex has started")

          runBlocking {

            val startOutcome = MockTask.startMLSession(
              callParameters = Environment.of(),
              permanentSessionEnvironment = Environment.of(
                TierCompletionSession with CompletionSession(
                  language = PlainTextLanguage.INSTANCE,
                  callOrder = 1,
                  completionType = CompletionType.SMART
                )
              )
            )

            val completionSession = startOutcome.session ?: return@runBlocking
            completionSession.withNestedSessions { lookupSessionCreator ->

              lookupSessionCreator.nestConsidering(Environment.of(), Environment.of(TierLookup with LookupImpl(true, 1)))
                .withPredictions {
                  it.predictConsidering(Environment.of(), Environment.of(TierItem with LookupItem("hello", emptyMap())))
                  it.predictConsidering(Environment.of(), Environment.of(TierItem with LookupItem("world", emptyMap())))
                }

              lookupSessionCreator.nestConsidering(Environment.of(), Environment.of(TierLookup with LookupImpl(false, 2)))
                .withPredictions {
                  it.predictConsidering(Environment.of(), Environment.of(TierItem with LookupItem("hello!!!", mapOf("bold" to true))))
                  it.predictConsidering(Environment.of(), Environment.of(TierItem with LookupItem("AAAAA!!", mapOf("strikethrough" to true))))
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("AAAAAAAAAAAAAAAAA", mapOf("cursive" to true))))
                }
            }
          }

          Thread.sleep(3 * 1000)

          println("Demo session #$sessionIndex has finished")
        }
      }
    }

    //val jsonSaver = MLLogsToJsonSaver(Path.of(".") / "testResources")
    //jsonSaver.save(logs, "ml_logs")
  }
}
