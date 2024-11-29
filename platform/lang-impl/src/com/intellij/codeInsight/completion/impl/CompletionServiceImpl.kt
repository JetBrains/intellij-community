// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionPhase.*
import com.intellij.codeInsight.completion.StatisticsWeigher.LookupStatisticsWeigher
import com.intellij.codeInsight.lookup.Classifier
import com.intellij.codeInsight.lookup.ClassifierFactory
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.util.CodeCompletion
import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.isCurrentlyUnderLocalId
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager.Companion.getAppSession
import com.intellij.openapi.client.forEachSession
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.patterns.ElementPattern
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.Weigher
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions

private val LOG = logger<CompletionServiceImpl>()
private val DEFAULT_PHASE_HOLDER = CompletionPhaseHolder(NoCompletion, null)

open class CompletionServiceImpl : BaseCompletionService() {
  private val completionTracer = TelemetryManager.getInstance().getTracer(CodeCompletion)

  companion object {
    @JvmStatic
    val completionService: CompletionServiceImpl
      get() = getCompletionService() as CompletionServiceImpl

    @JvmStatic
    val currentCompletionProgressIndicator: CompletionProgressIndicator?
      get() = tryGetClientCompletionService(getAppSession())?.currentCompletionProgressIndicator

    @SafeVarargs
    @JvmStatic
    fun assertPhase(vararg possibilities: Class<out CompletionPhase>) {
      val holder = tryGetClientCompletionService(getAppSession())?.completionPhaseHolder ?: DEFAULT_PHASE_HOLDER
      if (!isPhase(holder.phase, *possibilities)) {
        reportPhase(holder)
      }
    }

    @SafeVarargs
    @JvmStatic
    fun isPhase(vararg possibilities: Class<out CompletionPhase>): Boolean {
      return isPhase(phase = completionPhase, possibilities = possibilities)
    }

    @JvmStatic
    val completionPhase: CompletionPhase
      get() {
        val clientCompletionService = tryGetClientCompletionService(getAppSession()) ?: return DEFAULT_PHASE_HOLDER.phase
        return clientCompletionService.completionPhase
      }

    // Keep the function for compatibility with external plugins
    @JvmStatic
    fun setCompletionPhase(phase: CompletionPhase) {
      LOG.trace("Set completion phase :: phase=$phase")
      val clientCompletionService = tryGetClientCompletionService(getAppSession()) ?: return
      clientCompletionService.completionPhase = phase
    }
  }

  init {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        application.forEachSession(ClientKind.ALL) { session ->
          val clientCompletionService = tryGetClientCompletionService(session) ?: return@forEachSession
          val indicator = clientCompletionService.currentCompletionProgressIndicator
          if (indicator != null && indicator.project === project) {
            indicator.closeAndFinish(true)
            clientCompletionService.completionPhase = NoCompletion
          }
          else if (indicator == null) {
            clientCompletionService.completionPhase = NoCompletion
          }
        }
      }
    })
    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        application.forEachSession(ClientKind.ALL) { session ->
          val clientCompletionService = tryGetClientCompletionService(session) ?: return@forEachSession
          clientCompletionService.completionPhase = NoCompletion
        }
      }
    })
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun setAdvertisementText(text: @NlsContexts.PopupAdvertisement String?) {
    if (text == null) {
      return
    }
    currentCompletionProgressIndicator?.addAdvertisement(text, null)
  }

  override fun createResultSet(parameters: CompletionParameters,
                               consumer: Consumer<in CompletionResult>,
                               contributor: CompletionContributor,
                               matcher: PrefixMatcher): CompletionResultSet {
    return CompletionResultSetImpl(consumer = consumer,
                                   prefixMatcher = matcher,
                                   contributor = contributor,
                                   parameters = parameters,
                                   sorter = null,
                                   original = null)
  }

  override fun getCurrentCompletion(): CompletionProcess? {
    currentCompletionProgressIndicator?.let {
      return it
    }
    // TODO we have to move myApiCompletionProcess inside per client service somehow
    // also shouldn't we delegate here to base method instead of accessing the field of the base class?
    return if (isCurrentlyUnderLocalId) apiCompletionProcess else null
  }

  private class CompletionResultSetImpl(consumer: java.util.function.Consumer<in CompletionResult>?,
                                        prefixMatcher: PrefixMatcher?,
                                        contributor: CompletionContributor?,
                                        parameters: CompletionParameters?,
                                        sorter: CompletionSorter?,
                                        original: CompletionResultSetImpl?) :
    BaseCompletionResultSet(consumer, prefixMatcher, contributor, parameters, sorter, original) {
    override fun addAllElements(elements: Iterable<LookupElement>) {
      CompletionThreadingBase.withBatchUpdate({ super.addAllElements(elements) }, parameters.process)
    }

    override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet {
      if (matcher == prefixMatcher) {
        return this
      }

      return CompletionResultSetImpl(consumer = consumer,
                                     prefixMatcher = matcher,
                                     contributor = contributor,
                                     parameters = parameters,
                                     sorter = sorter,
                                     original = this)
    }

    override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet {
      return CompletionResultSetImpl(consumer = consumer,
                                     prefixMatcher = prefixMatcher,
                                     contributor = contributor,
                                     parameters = parameters,
                                     sorter = sorter,
                                     original = this)
    }

    override fun addLookupAdvertisement(text: String) {
      completionService.setAdvertisementText(text)
    }

    override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {
      val process = parameters.process
      if (process is CompletionProcessBase) {
        process.addWatchedPrefix(parameters.offset - prefixMatcher.prefix.length, prefixCondition)
      }
    }

    override fun restartCompletionWhenNothingMatches() {
      val process = parameters.process
      if (process is CompletionProgressIndicator) {
        process.lookup.isStartCompletionWhenNothingMatches = true
      }
    }
  }

  override fun addWeighersBefore(sorter: CompletionSorterImpl): CompletionSorterImpl {
    val processed = super.addWeighersBefore(sorter)
    return processed.withClassifier(CompletionSorterImpl.weighingFactory(LiveTemplateWeigher()))
  }

  override fun processStatsWeigher(sorter: CompletionSorterImpl,
                                   weigher: Weigher<*, *>,
                                   location: CompletionLocation): CompletionSorterImpl {
    val processedSorter = super.processStatsWeigher(sorter, weigher, location)
    return processedSorter.withClassifier(object : ClassifierFactory<LookupElement>("stats") {
      override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
        return LookupStatisticsWeigher(location, next)
      }
    })
  }

  override fun getVariantsFromContributor(params: CompletionParameters, contributor: CompletionContributor, result: CompletionResultSet) {
    completionTracer.spanBuilder(contributor.javaClass.simpleName)
      .setAttribute("avoid_null_value", true)
      .use {
        super.getVariantsFromContributor(params, contributor, result)
      }
  }

  override fun performCompletion(parameters: CompletionParameters, consumer: Consumer<in CompletionResult>) {
    completionTracer.spanBuilder("performCompletion").use { span ->
      val countingConsumer = object : Consumer<CompletionResult> {
        @JvmField
        var count: Int = 0

        override fun consume(result: CompletionResult) {
          count++
          consumer.consume(result)
        }
      }
      super.performCompletion(parameters, countingConsumer)
      span.setAttribute("lookupsFound", countingConsumer.count.toLong())
    }
  }
}

private class ClientCompletionService(private val appSession: ClientAppSession) : Disposable {
  @Volatile
  var completionPhaseHolder: CompletionPhaseHolder = DEFAULT_PHASE_HOLDER
    private set

  override fun dispose() {
    Disposer.dispose(completionPhaseHolder.phase)
  }

  var completionPhase: CompletionPhase
    get() = completionPhaseHolder.phase
    set(phase) {
      ThreadingAssertions.assertEventDispatchThread()
      val oldPhase = this.completionPhase
      val oldIndicator = oldPhase.indicator
      if (oldIndicator != null && phase !is BgCalculation && oldIndicator.isRunning && !oldIndicator.isCanceled) {
        LOG.error("don't change phase during running completion: oldPhase=$oldPhase")
      }
      val wasCompletionRunning = isRunningPhase(oldPhase)
      val isCompletionRunning = isRunningPhase(phase)
      if (isCompletionRunning != wasCompletionRunning) {
        ApplicationManager.getApplication().messageBus.syncPublisher(CompletionPhaseListener.TOPIC)
          .completionPhaseChanged(isCompletionRunning)
      }

      LOG.trace("Dispose old phase :: oldPhase=$oldPhase, newPhase=$phase, indicator=${if (phase.indicator != null) phase.indicator.hashCode() else -1}")
      Disposer.dispose(oldPhase)
      completionPhaseHolder = CompletionPhaseHolder(phase = phase, phaseTrace = Throwable())
    }

  val currentCompletionProgressIndicator: CompletionProgressIndicator?
    get() = getCurrentCompletionProgressIndicator(this.completionPhase)

  fun getCurrentCompletionProgressIndicator(phase: CompletionPhase): CompletionProgressIndicator? {
    if (isPhase(phase, BgCalculation::class.java, ItemsCalculated::class.java, CommittingDocuments::class.java, Synchronous::class.java)) {
      return phase.indicator
    }
    return null
  }
}

private fun tryGetClientCompletionService(session: ClientAppSession?): ClientCompletionService? {
  return session?.getService(ClientCompletionService::class.java)
}

@SafeVarargs
private fun isPhase(phase: CompletionPhase, vararg possibilities: Class<out CompletionPhase>): Boolean {
  return possibilities.any { it.isInstance(phase) }
}

private fun isRunningPhase(phase: CompletionPhase): Boolean {
  return phase !== NoCompletion && phase !is ZombiePhase && phase !is ItemsCalculated
}

private fun reportPhase(phaseHolder: CompletionPhaseHolder) {
  val phaseTrace = phaseHolder.phaseTrace
  val traceText = if (phaseTrace == null) "" else "; set at ${ExceptionUtil.getThrowableText(phaseTrace)}"
  LOG.error("${phaseHolder.phase}; $current$traceText")
}

private data class CompletionPhaseHolder(@JvmField val phase: CompletionPhase, @JvmField val phaseTrace: Throwable?)

