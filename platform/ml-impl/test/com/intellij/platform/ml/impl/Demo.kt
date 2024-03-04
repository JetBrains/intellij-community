// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsageCollectorEP
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors.COUNTER_EP_NAME
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.impl.apiPlatform.CodeLikePrinter
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.logs.InplaceFeaturesScheme
import com.intellij.platform.ml.impl.logs.events.registerEventSessionFailed
import com.intellij.platform.ml.impl.logs.events.registerEventSessionFinished
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.*
import com.intellij.platform.ml.impl.session.analysis.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.application
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.io.path.div
import kotlin.random.Random

enum class CompletionType {
  SMART,
  BASIC
}

data class CompletionSession(
  val language: Language,
  val callOrder: Int,
  val completionType: CompletionType?
)

data class LookupImpl(
  val foo: Boolean,
  val index: Int
)

data class LookupItem(
  val lookupString: String,
  val decoration: Map<Any, Any>
)

data class GitRepository(
  val user: String,
  val projectUri: URI,
  val commits: List<String>
)

object TierCompletionSession : Tier<CompletionSession>()
object TierLookup : Tier<LookupImpl>()
object TierItem : Tier<LookupItem>()

object TierGit : Tier<GitRepository>()

class CompletionSessionFeatures1 : TierDescriptor.Default(TierCompletionSession) {
  companion object {
    val CALL_ORDER = FeatureDeclaration.int("call_order")
    val LANGUAGE_ID = FeatureDeclaration.categorical("language_id", Language.getRegisteredLanguages().map { it.id }.toSet())
    val GIT_USER = FeatureDeclaration.boolean("git_user_is_Glebanister")
    val COMPLETION_TYPE = FeatureDeclaration.enum<CompletionType>("completion_type").nullable()
  }

  override val descriptionPolicy: DescriptionPolicy = DescriptionPolicy(false, false)

  override val additionallyRequiredTiers: Set<Tier<*>> = setOf(TierGit)

  override val descriptionDeclaration: Set<FeatureDeclaration<*>> = setOf(
    CALL_ORDER, LANGUAGE_ID, GIT_USER, COMPLETION_TYPE
  )

  override suspend fun describe(environment: Environment, usefulFeaturesFilter: FeatureFilter): Set<Feature> {
    val completionSession = environment[TierCompletionSession]
    val gitRepository = environment[TierGit]
    return setOf(
      CALL_ORDER with completionSession.callOrder,
      LANGUAGE_ID with completionSession.language.id,
      GIT_USER with (gitRepository.user == "Glebanister"),
      COMPLETION_TYPE with completionSession.completionType
    )
  }
}

class ItemFeatures1 : TierDescriptor.Default(TierItem) {
  companion object {
    val DECORATIONS = FeatureDeclaration.int("decorations")
    val LENGTH = FeatureDeclaration.int("length")
  }

  override val descriptionPolicy: DescriptionPolicy = DescriptionPolicy(false, false)

  override val descriptionDeclaration: Set<FeatureDeclaration<*>> = setOf(
    DECORATIONS, LENGTH
  )

  override suspend fun describe(environment: Environment, usefulFeaturesFilter: FeatureFilter): Set<Feature> {
    val item = environment[TierItem]
    return setOf(
      DECORATIONS with item.decoration.size,
      LENGTH with item.lookupString.length
    )
  }
}

class GitFeatures1 : TierDescriptor.Default(TierGit) {
  companion object {
    val N_COMMITS = FeatureDeclaration.int("n_commits")
    val HAS_USER = FeatureDeclaration.boolean("has_user")
  }

  override val descriptionPolicy: DescriptionPolicy = DescriptionPolicy(false, false)

  override val descriptionDeclaration: Set<FeatureDeclaration<*>> = setOf(
    N_COMMITS, HAS_USER
  )

  override suspend fun describe(environment: Environment, usefulFeaturesFilter: FeatureFilter) = setOf(
    N_COMMITS with environment[TierGit].commits.size,
    HAS_USER with environment[TierGit].user.isNotEmpty()
  )
}

class GitInformant : EnvironmentExtender<GitRepository> {
  override val extendingTier: Tier<GitRepository> = TierGit

  override val requiredTiers: Set<Tier<*>> = setOf()

  override fun extend(environment: Environment): GitRepository {
    return GitRepository(
      user = "Glebanister",
      projectUri = URI.create("ssh://git@git.jetbrains.team/ij/intellij.git"),
      commits = listOf(
        "0e47200fa3bf029d7244745eacbf9d495de818c1",
        "638fbc7840b85d6e34ef2320ca5b2c9ec2c4b23c",
        "5a74104a0901b8faa4cc1f76736347cd33917041"
      )
    )
  }
}

class SomeStructureAnalyser<M : MLModel<Double>> : StructureAnalyser<M, Double> {
  companion object {
    val SESSION_IS_GOOD = FeatureDeclaration.boolean("very_good_session")
    val LOOKUP_INDEX = FeatureDeclaration.int("lookup_index")
  }

  override fun analyse(sessionTreeRoot: DescribedRootContainer<M, Double>): CompletableFuture<StructureAnalysis<M, Double>> {
    val analysis = mutableMapOf<DescribedSessionTree<M, Double>, PerTier<Set<Feature>>>()
    sessionTreeRoot.accept(LookupAnalyser(analysis))
    analysis[sessionTreeRoot] = mapOf(
      TierCompletionSession to setOf(SESSION_IS_GOOD with true)
    )

    return CompletableFuture.supplyAsync {
      // Pretend that analysis is taking some long time
      TimeUnit.SECONDS.sleep(2)
      analysis
    }
  }

  private class LookupAnalyser<M : MLModel<Double>>(
    private val analysis: MutableMap<DescribedSessionTree<M, Double>, PerTier<Set<Feature>>>
  ) : SessionTree.LevelVisitor<M, Double>(levelIndex = 1) {
    override fun visitLevel(level: DescribedLevel, levelRoot: DescribedSessionTree<M, Double>) {
      val lookup = level.environment[TierLookup]
      analysis[levelRoot] = mapOf(TierLookup to setOf(LOOKUP_INDEX with lookup.index))
    }
  }

  override val analysisDeclaration: PerTier<Set<FeatureDeclaration<*>>>
    get() = mapOf(
      TierCompletionSession to setOf(SESSION_IS_GOOD),
      TierLookup to setOf(LOOKUP_INDEX)
    )
}

class RandomModelSeedAnalyser : MLModelAnalyser<RandomModel, Double> {
  companion object {
    val SEED = FeatureDeclaration.int("random_seed")
  }

  override val analysisDeclaration: Set<FeatureDeclaration<*>> = setOf(
    SEED
  )

  override fun analyse(sessionTreeRoot: DescribedRootContainer<RandomModel, Double>): CompletableFuture<Set<Feature>> {
    return CompletableFuture.supplyAsync {
      // Pretend that analysis is taking some long time
      TimeUnit.SECONDS.sleep(1)
      setOf(SEED with sessionTreeRoot.rootData.seed)
    }
  }
}

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

  override val languageId: String = PlainTextLanguage.INSTANCE.id

  override val version: Version = Version(0, 0, 1)
}

class SomeListener(private val name: String) : MLTaskGroupListener {
  override val approachListeners = listOf(
    MockTaskApproachBuilder::class.java monitoredBy InitializationListener()
  )

  private fun log(message: String) = println("[Listener $name says] $message")

  inner class InitializationListener : MLApproachInitializationListener<RandomModel, Double> {
    override fun onAttemptedToStartSession(permanentSessionEnvironment: Environment): MLApproachListener<RandomModel, Double> {
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

    override fun onStartedSession(session: Session<Double>): MLSessionListener<RandomModel, Double> {
      log("session was started successfully: $session")
      return SessionListener()
    }
  }

  inner class SessionListener : MLSessionListener<RandomModel, Double> {
    override fun onSessionDescriptionFinished(sessionTree: DescribedRootContainer<RandomModel, Double>) {
      log("session successfully described: $sessionTree")
    }

    override fun onSessionAnalysisFinished(sessionTree: AnalysedRootContainer<Double>) {
      log("session successfully analyzed: $sessionTree")
    }
  }
}

object ExceptionLogger : ShallowSessionAnalyser<Throwable> {
  private val THROWABLE_CLASS = ClassEventField("throwable_class")

  override val name: String = "exception"

  override val declaration: List<EventField<*>> = listOf(THROWABLE_CLASS)

  override fun analyse(permanentSessionEnvironment: Environment, data: Throwable): List<EventPair<*>> {
    return listOf(THROWABLE_CLASS with data.javaClass)
  }
}

object FailureLogger : ShallowSessionAnalyser<Session.StartOutcome.Failure<Double>> {
  private val REASON = ClassEventField("reason")

  override val name: String = "normal_failure"

  override val declaration: List<EventField<*>> = listOf(REASON)

  override fun analyse(permanentSessionEnvironment: Environment,
                       data: Session.StartOutcome.Failure<Double>): List<EventPair<*>> {
    return listOf(REASON with data.javaClass)
  }
}

class MockTaskFusLogger : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("mock-task", 1).also {
      it.registerEventSessionFailed<RandomModel, Double>("failed", MockTask, listOf(ExceptionLogger), listOf(FailureLogger))
      it.registerEventSessionFinished<RandomModel, Double>("finished", MockTask, InplaceFeaturesScheme.FusScheme.DOUBLE)
    }
  }

  override fun getGroup() = GROUP
}

object ThisTestApiPlatform : TestApiPlatform() {
  override val tierDescriptors = listOf(
    CompletionSessionFeatures1(),
    ItemFeatures1(),
    GitFeatures1(),
  )

  override val environmentExtenders = listOf(
    GitInformant(),
  )

  override val taskApproaches = listOf(
    MockTaskApproachBuilder()
  )

  override val initialTaskListeners: List<MLTaskGroupListener> = listOf(
    SomeListener("Nika"),
    SomeListener("Alex"),
  )


  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) {
    val printer = CodeLikePrinter()
    println("$descriptor is missing the following declaration: ${printer.printCodeLikeString(nonDeclaredFeatures.map { it.declaration })}")
  }
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


class MockTaskApproachDetails : LogDrivenModelInference.SessionDetails<RandomModel, Double> {
  override val additionallyDescribedTiers: List<Set<Tier<*>>> = listOf(
    setOf(TierGit),
    setOf(),
    setOf(),
  )

  override val mlModelAnalysers: Collection<MLModelAnalyser<RandomModel, Double>>
    get() = listOf(
      RandomModelSeedAnalyser(),
      ModelVersionAnalyser(),
      ModelLanguageAnalyser()
    )

  override val structureAnalysers: Collection<StructureAnalyser<RandomModel, Double>>
    get() = listOf(SomeStructureAnalyser())

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

class MockTaskApproachBuilder : LogDrivenModelInference.Builder<RandomModel, Double>(MockTask, MockTaskApproachDetails.Builder())

class TestTask : BasePlatformTestCase() {
  fun `test demo ml task`() {
    // After the session is finished, it will be logged to community/platform/ml-impl/testResources/ml_logs.js

    val logs: MutableList<Pair<String, Map<String, Any>>> = mutableListOf()
    val collectLogs = Consumer { fusLog: LogEvent ->
      logs.add(fusLog.event.id to fusLog.event.data)
    }

    // TODO: Handle this as well (when it has been initialized before we had the control flow)
    //MLEventLogger.Manager.ensureNotInitialized()

    ReplaceableIJPlatform.replacingWith(ThisTestApiPlatform) {
      val logger = MockTaskFusLogger()
      val loggerEP = CounterUsageCollectorEP()
      application.extensionArea.getExtensionPoint(COUNTER_EP_NAME).registerExtension(loggerEP, this.testRootDisposable)

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

          // Wait until the analysis will be over
          // TODO: Think if it is possible to track it via API

          Thread.sleep(3 * 1000)

          println("Demo session #$sessionIndex has finished")
        }
      }
    }

    val jsonSaver = MLLogsToJsonSaver(Path.of(".") / "testResources")

    jsonSaver.save(logs)
  }
}
