// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors.COUNTER_EP_NAME
import com.intellij.lang.Language
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.platform.ml.*
import com.intellij.platform.ml.analysis.SessionAnalyser
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.environment.EnvironmentExtender
import com.intellij.platform.ml.environment.get
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureFilter
import com.intellij.platform.ml.logs.schema.ClassEventField
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import com.intellij.testFramework.ParsingTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.application
import java.net.URI


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

class CompletionSessionFeatures : TierDescriptor.Default(TierCompletionSession) {
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

class ExceptionLogger<M : MLModel<P>, P : Any> : SessionAnalyser.Default<M, P>() {
  private val THROWABLE_CLASS = ClassEventField("throwable_class", null)

  override val declaration: List<EventField<*>> = listOf(THROWABLE_CLASS)
  override suspend fun onSessionFailedWithException(callParameters: Environment, sessionEnvironment: Environment, exception: Throwable): List<EventPair<*>> {
    return listOf(THROWABLE_CLASS with exception.javaClass)
  }
}

class FailureLogger<M : MLModel<P>, P : Any> : SessionAnalyser.Default<M, P>() {
  private val REASON = ClassEventField("reason", null)

  override val declaration: List<EventField<*>> = listOf(REASON)

  override suspend fun onSessionFailedToStart(callParameters: Environment, sessionEnvironment: Environment, failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> {
    return listOf(REASON with failure.javaClass)
  }
}

abstract class MLApiLogsTestCase : BasePlatformTestCase() {
  private lateinit var counterUsagesCollectorEP: ExtensionPointImpl<CounterUsagesCollector>

  override fun setUp() {
    super.setUp()
    val area = application.extensionArea as ExtensionsAreaImpl
    val pluginDescriptor: PluginDescriptor = DefaultPluginDescriptor(PluginId.getId(javaClass.name + "." + name), ParsingTestCase::class.java.classLoader)
    area.unregisterExtensionPoint(COUNTER_EP_NAME.name)
    counterUsagesCollectorEP = area.registerFakeBeanPoint(COUNTER_EP_NAME.name, pluginDescriptor)
  }

  fun registerEventLogger(counterUsagesCollector: CounterUsagesCollector) {
    counterUsagesCollectorEP.registerExtension(counterUsagesCollector, testRootDisposable)
  }
}
