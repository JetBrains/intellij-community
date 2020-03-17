// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.impl.*
import com.intellij.codeInsight.completion.impl.LiftShorterItemsClassifier.LiftingCondition
import com.intellij.codeInsight.lookup.Classifier
import com.intellij.codeInsight.lookup.ClassifierFactory
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.ElementPattern
import com.intellij.psi.Weigher
import com.intellij.psi.WeighingService
import com.intellij.psi.impl.DebugUtil
import com.intellij.util.Consumer

open class BaseCompletionService : CompletionService() {
  @JvmField
  protected var myApiCompletionProcess: CompletionProcessEx? = null

  override fun setAdvertisementText(text: String?) {
    if (text == null) return
    myApiCompletionProcess?.addAdvertisement(text, null)
  }

  override fun performCompletion(parameters: CompletionParameters, consumer: Consumer<in CompletionResult>) {
    myApiCompletionProcess = parameters.process as CompletionProcessEx
    try {
      super.performCompletion(parameters, consumer)
    }
    finally {
      myApiCompletionProcess = null
    }
  }

  override fun createResultSet(parameters: CompletionParameters,
                               consumer: Consumer<in CompletionResult>,
                               contributor: CompletionContributor,
                               matcher: PrefixMatcher): CompletionResultSet {
    return BaseCompletionResultSet(consumer, matcher, contributor, parameters, defaultSorter(parameters, matcher), null)
  }

  override fun suggestPrefix(parameters: CompletionParameters): String {
    val position = parameters.position
    val offset = parameters.offset
    val range = position.textRange
    assert(range.containsOffset(offset)) { "$position; $offset not in $range" }
    @Suppress("DEPRECATION")
    return CompletionData.findPrefixStatic(position, offset)
  }

  override fun createMatcher(prefix: String, typoTolerant: Boolean): PrefixMatcher = createMatcher(prefix, true, typoTolerant)

  override fun getCurrentCompletion(): CompletionProcess? = myApiCompletionProcess

  override fun defaultSorter(parameters: CompletionParameters?, matcher: PrefixMatcher?): CompletionSorterImpl {
    val location = CompletionLocation(parameters)

    var sorter = emptySorter()
    sorter = addWeighersBefore(sorter)
    //sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(LiveTemplateWeigher()))
    sorter = sorter.withClassifier(CompletionSorterImpl.weighingFactory(PreferStartMatching()))

    for (weigher in WeighingService.getWeighers(RELEVANCE_KEY)) {
      val id = weigher.toString()
      sorter = when (id) {
        "prefix" -> {
          sorter.withClassifier(CompletionSorterImpl.weighingFactory(RealPrefixMatchingWeigher()))
        }
        "stats" -> {
          processStatsWeigher(sorter, weigher, location)
        }
        else -> {
          sorter.weigh(object : LookupElementWeigher(id, true, false) {
            override fun weigh(element: LookupElement): Comparable<*>? {
              return weigher.weigh(element, location)
            }
          })
        }
      }
    }

    return sorter.withClassifier("priority", true, object : ClassifierFactory<LookupElement>("liftShorter") {
      override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
        return LiftShorterItemsClassifier("liftShorter", next, LiftingCondition(), false)
      }
    })
  }

  protected open fun addWeighersBefore(sorter: CompletionSorterImpl): CompletionSorterImpl = sorter

  protected open fun processStatsWeigher(sorter: CompletionSorterImpl,
                                         weigher: Weigher<Any, Any>,
                                         location: CompletionLocation): CompletionSorterImpl = sorter

  override fun emptySorter(): CompletionSorterImpl = CompletionSorterImpl(listOf())

  protected open class BaseCompletionResultSet(consumer: Consumer<in CompletionResult>,
                                               matcher: PrefixMatcher,
                                               contributor: CompletionContributor,
                                               @JvmField protected val myParameters: CompletionParameters,
                                               @JvmField protected val mySorter: CompletionSorterImpl,
                                               @JvmField protected val myOriginal: BaseCompletionResultSet?) :
    CompletionResultSet(matcher, consumer, contributor) {
    override fun addElement(element: LookupElement) {
      ProgressManager.checkCanceled()
      if (!element.isValid) {
        LOG.error("Invalid lookup element: " + element + " of " + element.javaClass +
                  " in " + myParameters.originalFile + " of " + myParameters.originalFile.javaClass)
        return
      }

      val matched = CompletionResult.wrap(element, prefixMatcher, mySorter)
      matched?.let { passResult(it) }
    }

    override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet =
      if (matcher == prefixMatcher) this
      else BaseCompletionResultSet(consumer, matcher, myContributor, myParameters, mySorter, this)

    override fun withPrefixMatcher(prefix: String): CompletionResultSet = withPrefixMatcher(prefixMatcher.cloneWithPrefix(prefix))

    override fun stopHere() {
      if (LOG.isTraceEnabled) {
        LOG.trace("Completion stopped\n" + DebugUtil.currentStackTrace())
      }
      super.stopHere()
      myOriginal?.stopHere()
    }

    override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet =
      BaseCompletionResultSet(consumer, prefixMatcher, myContributor, myParameters, sorter as CompletionSorterImpl, this)

    override fun addLookupAdvertisement(text: String) {
      (getCompletionService().currentCompletion as CompletionProcessEx?)?.addAdvertisement(text, null)
    }

    override fun caseInsensitive(): CompletionResultSet {
      val matcher = prefixMatcher
      val typoTolerant = matcher is CamelHumpMatcher && matcher.isTypoTolerant
      return withPrefixMatcher(createMatcher(matcher.prefix, false, typoTolerant))
    }

    override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>?) {
    }

    override fun restartCompletionWhenNothingMatches() {
    }
  }

  companion object {
    private val LOG = logger<BaseCompletionService>()

    private fun createMatcher(prefix: String, caseSensitive: Boolean, typoTolerant: Boolean): CamelHumpMatcher {
      return CamelHumpMatcher(prefix, caseSensitive, typoTolerant)
    }
  }
}