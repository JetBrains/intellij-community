// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.concurrency.JobLauncher
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.proximity.PsiProximityComparator
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.Processor
import com.intellij.util.SynchronizedCollectConsumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlin.math.max

open class DefaultChooseByNameItemProvider(context: PsiElement?) : ChooseByNameInScopeItemProvider {
  private val myContext: SmartPsiElementPointer<PsiElement>? = context?.createSmartPointer()

  override fun filterElements(
    base: ChooseByNameViewModel,
    pattern: String,
    everywhere: Boolean,
    indicator: ProgressIndicator,
    consumer: Processor<Any>
  ): Boolean {
    return filterElementsWithWeights(base, createParameters(base, pattern, everywhere), indicator,
                                     Processor { res: FoundItemDescriptor<*>? -> consumer.process(res!!.getItem()) })
  }

  override fun filterElements(
    base: ChooseByNameViewModel,
    parameters: FindSymbolParameters,
    indicator: ProgressIndicator,
    consumer: Processor<Any>
  ): Boolean {
    return filterElementsWithWeights(base, parameters, indicator, Processor { res: FoundItemDescriptor<*>? ->
      consumer.process(
        res!!.getItem())
    })
  }

  override fun filterElementsWithWeights(
    base: ChooseByNameViewModel,
    pattern: String,
    everywhere: Boolean,
    indicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<*>>
  ): Boolean {
    return filterElementsWithWeights(base, createParameters(base, pattern, everywhere), indicator, consumer)
  }

  override fun filterElementsWithWeights(
    base: ChooseByNameViewModel,
    parameters: FindSymbolParameters,
    indicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<*>>
  ): Boolean {
    return ProgressManager.getInstance().computePrioritized<Boolean?, RuntimeException?>(
      ThrowableComputable {
        filterElements(base,
                       indicator,
                       myContext?.getElement(),
                       Supplier { base.getModel().getNames(parameters.isSearchInLibraries) },
                       consumer,
                       parameters)
      })
  }

  protected val pathProximityComparator: Comparator<Any>
    get() = PathProximityComparator(myContext?.getElement())

  override fun filterNames(base: ChooseByNameViewModel, names: Array<String?>, pattern: String): MutableList<String?> {
    var pattern = pattern
    val preferStartMatches = pattern.startsWith("*")
    pattern = convertToMatchingPattern(base, pattern)
    if (pattern.isEmpty() && !base.canShowListForEmptyPattern()) return mutableListOf<String?>()

    val filtered: MutableList<String?> = ArrayList<String?>()
    processNamesByPattern(base, names, pattern, ProgressIndicatorProvider.getGlobalProgressIndicator(), Consumer { result: MatchResult? ->
      synchronized(filtered) {
        filtered.add(
          result!!.elementName)
      }
    }, preferStartMatches)
    synchronized(filtered) {
      return filtered
    }
  }

  private class PathProximityComparator(context: PsiElement?) : Comparator<Any> {
    private val myProximityComparator: PsiProximityComparator = PsiProximityComparator(context)

    override fun compare(o1: Any, o2: Any): Int {
      val rc = myProximityComparator.compare(o1, o2)
      if (rc != 0) return rc

      val o1Weight = if (isCompiledWithoutSource(o1)) 1 else 0
      val o2Weight = if (isCompiledWithoutSource(o2)) 1 else 0
      return o1Weight - o2Weight
    }

    fun isCompiledWithoutSource(o: Any): Boolean {
      return o is PsiCompiledElement && o.getNavigationElement() === o
    }
  }

  @ApiStatus.Internal
  companion object {
    private val LOG = Logger.getInstance(DefaultChooseByNameItemProvider::class.java)
    private const val UNIVERSAL_SEPARATOR = "\u0000"

    private fun filterElements(
      base: ChooseByNameViewModel,
      indicator: ProgressIndicator,
      context: PsiElement?,
      allNamesProducer: Supplier<Array<String?>?>?,
      consumer: Processor<in FoundItemDescriptor<*>>,
      parameters: FindSymbolParameters
    ): Boolean {
      val everywhere = parameters.isSearchInLibraries
      val pattern = parameters.completePattern
      if (base.getProject() != null) {
        base.getProject().putUserData<String?>(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, pattern)
      }

      val namePattern: String = getNamePattern(base, pattern)
      val preferStartMatches = !pattern.startsWith("*")

      val namesList: MutableList<MatchResult> = getSortedNamesForAllWildcards(base, parameters, indicator, allNamesProducer, namePattern,
                                                                              preferStartMatches)

      indicator.checkCanceled()

      return processByNames(base, everywhere, indicator, context, consumer, namesList, parameters)
    }

    private fun getSortedNamesForAllWildcards(
      base: ChooseByNameViewModel,
      parameters: FindSymbolParameters,
      indicator: ProgressIndicator,
      allNamesProducer: Supplier<Array<String?>?>?,
      namePattern: String,
      preferStartMatches: Boolean
    ): MutableList<MatchResult> {
      val matchingPattern: String = convertToMatchingPattern(base, namePattern)
      if (matchingPattern.isEmpty() && !base.canShowListForEmptyPattern()) return mutableListOf<MatchResult>()

      val result: MutableList<MatchResult> = getSortedNames(base, parameters, indicator, allNamesProducer, matchingPattern,
                                                            preferStartMatches)
      if (!namePattern.contains("*")) return result

      val allNames: MutableSet<String> = HashSet<String>(result.map { it.elementName })
      for (i in 1..<namePattern.length - 1) {
        if (namePattern[i] == '*') {
          val namesForSuffix: MutableList<MatchResult> = getSortedNames(base, parameters, indicator, allNamesProducer,
                                                                        convertToMatchingPattern(base, namePattern.substring(i + 1)),
                                                                        preferStartMatches)
          for (mr in namesForSuffix) {
            if (allNames.add(mr.elementName)) {
              result.add(mr)
            }
          }
        }
      }
      return result
    }

    private fun getSortedNames(
      base: ChooseByNameViewModel,
      parameters: FindSymbolParameters,
      indicator: ProgressIndicator,
      allNamesProducer: Supplier<Array<String?>?>?,
      namePattern: String, preferStartMatches: Boolean
    ): MutableList<MatchResult> {
      val namesList: MutableList<MatchResult> = getAllNames(base, parameters, indicator, allNamesProducer, namePattern, preferStartMatches)

      indicator.checkCanceled()
      val pattern = parameters.completePattern

      val started = System.currentTimeMillis()
      namesList.sortWith(Comparator.comparing<MatchResult?, Boolean?>(java.util.function.Function { mr: MatchResult? ->
        !pattern.equals(
          mr!!.elementName, ignoreCase = true)
      })
                       .thenComparing<Boolean?>(
                         java.util.function.Function { mr: MatchResult? -> !namePattern.equals(mr!!.elementName, ignoreCase = true) })
                       .thenComparing(Comparator.naturalOrder<MatchResult?>()))
      if (LOG.isDebugEnabled()) {
        LOG.debug("sorted:" + (System.currentTimeMillis() - started) + ",results:" + namesList.size)
      }
      return namesList
    }

    private fun getAllNames(
      base: ChooseByNameViewModel,
      parameters: FindSymbolParameters,
      indicator: ProgressIndicator,
      allNamesProducer: Supplier<Array<String?>?>?,
      namePattern: String,
      preferStartMatches: Boolean
    ): MutableList<MatchResult> {
      val namesList: MutableList<MatchResult> = ArrayList<MatchResult>()

      val collect: CollectConsumer<MatchResult?> = SynchronizedCollectConsumer<MatchResult?>(namesList)

      val model = base.getModel()
      if (model is ChooseByNameModelEx) {
        indicator.checkCanceled()
        val started = System.currentTimeMillis()
        val fullPattern = parameters.completePattern
        val matcher: MinusculeMatcher = buildPatternMatcher(namePattern, preferStartMatches)
        model.processNames(Processor { sequence: String? ->
          indicator.checkCanceled()
          val result: MatchResult? = matches(base, fullPattern, matcher, sequence)
          if (result != null) {
            collect.consume(result)
            return@Processor true
          }
          false
        }, parameters)
        if (LOG.isDebugEnabled()) {
          LOG.debug("loaded + matched:" + (System.currentTimeMillis() - started) + "," + collect.getResult().size)
        }
      }
      else {
        requireNotNull(allNamesProducer) { "Need to specify allNamesProducer when using a model which isn't a ChooseByNameModelEx" }
        val names: Array<String?> = allNamesProducer.get()!!
        val started = System.currentTimeMillis()
        processNamesByPattern(base, names, namePattern, indicator, collect, preferStartMatches)
        if (LOG.isDebugEnabled()) {
          LOG.debug("matched:" + (System.currentTimeMillis() - started) + "," + names.size)
        }
      }
      synchronized(collect) {
        return ArrayList<MatchResult>(namesList)
      }
    }

    private fun createParameters(base: ChooseByNameViewModel, pattern: String, everywhere: Boolean): FindSymbolParameters {
      val model = base.getModel()
      val idFilter = if (model is ContributorsBasedGotoByModel) IdFilter.getProjectIdFilter(
        model.project, everywhere)
      else null
      val searchScope = FindSymbolParameters.searchScopeFor(base.getProject(), everywhere)
      @Suppress("DEPRECATION")
      return FindSymbolParameters(pattern, getNamePattern(base, pattern), searchScope, idFilter)
    }

    private fun processByNames(
      base: ChooseByNameViewModel,
      everywhere: Boolean,
      indicator: ProgressIndicator,
      context: PsiElement?,
      consumer: Processor<in FoundItemDescriptor<*>>,
      namesList: MutableList<out MatchResult>,
      parameters: FindSymbolParameters
    ): Boolean {
      val model = base.getModel()
      @Suppress(
        "UNCHECKED_CAST") val modelComparator: Comparator<Any> = if (model is Comparator<*>) model as Comparator<Any> else PathProximityComparator(context)
      val weightComparator: Comparator<Pair<Any, MatchResult>> =
         compareBy<Pair<Any, MatchResult>, Any>(modelComparator) { it.first }
        .thenBy { it.second }

      val fullMatcher: MinusculeMatcher = getFullMatcher(parameters, base)

      val shouldProcess = AtomicBoolean(true)
      runBlockingMaybeCancellable {
        withContext(Dispatchers.Default) {
          namesList.forEachConcurrent { result ->
            if (shouldProcess.get()) {
              indicator.checkCanceled()
              val name = result.elementName

              // use interruptible call if possible
              val elements = if (model is ContributorsBasedGotoByModel)
                model.getElementsByName(name, parameters, indicator)
              else
                model.getElementsByName(name, everywhere, getNamePattern(base, parameters.completePattern))
              if (elements.size > 1) {
                val sameNameElements = elements.mapNotNull {
                  indicator.checkCanceled()
                  val result = matchQualifiedName(model, fullMatcher, it) ?: return@mapNotNull null
                  Pair.create<Any, MatchResult>(it, result)
                }.sortedWith(weightComparator)

                val processedItems = sameNameElements.map { FoundItemDescriptor<Any>(it.first, result.matchingDegree) }
                if (!ContainerUtil.process<FoundItemDescriptor<*>?>(processedItems, consumer)) {
                  shouldProcess.set(false)
                }
              }
              else if (elements.size == 1) {
                if (matchQualifiedName(model, fullMatcher, elements[0]) != null) {
                  if (!consumer.process(FoundItemDescriptor<Any>(elements[0], result.matchingDegree))) {
                    shouldProcess.set(false)
                  }
                }
              }
            }
          }
        }
      }
      return shouldProcess.get()
    }

    private fun getFullMatcher(parameters: FindSymbolParameters, base: ChooseByNameViewModel): MinusculeMatcher {
      val fullRawPattern: String = buildFullPattern(base, parameters.completePattern)
      val fullNamePattern: String = buildFullPattern(base, base.transformPattern(parameters.completePattern))

      return NameUtil.buildMatcherWithFallback(fullRawPattern, fullNamePattern, NameUtil.MatchingCaseSensitivity.NONE)
    }

    private fun buildFullPattern(base: ChooseByNameViewModel, pattern: String): String {
      var fullPattern = "*" + removeModelSpecificMarkup(base.getModel(), pattern)
      for (separator in base.getModel().getSeparators()) {
        fullPattern = StringUtil.replace(fullPattern, separator, "*$UNIVERSAL_SEPARATOR*")
      }
      return fullPattern
    }

    private fun getNamePattern(base: ChooseByNameViewModel, pattern: String): String {
      val transformedPattern = base.transformPattern(pattern)
      return getNamePattern(base.getModel(), transformedPattern)
    }

    private fun getNamePattern(model: ChooseByNameModel, pattern: String): String {
      val separators = model.getSeparators()
      var lastSeparatorOccurrence = 0
      for (separator in separators) {
        var idx = pattern.lastIndexOf(separator)
        if (idx == pattern.length - 1) {  // avoid empty name
          idx = pattern.lastIndexOf(separator, idx - 1)
        }
        lastSeparatorOccurrence = max(lastSeparatorOccurrence.toDouble(),
                                      (if (idx == -1) idx else idx + separator.length).toDouble()).toInt()
      }

      return pattern.substring(lastSeparatorOccurrence)
    }

    private fun matchQualifiedName(model: ChooseByNameModel, fullMatcher: MinusculeMatcher, element: Any): MatchResult? {
      var fullName = model.getFullName(element)
      if (fullName == null) return null

      for (separator in model.getSeparators()) {
        fullName = StringUtil.replace(fullName!!, separator, UNIVERSAL_SEPARATOR)
      }
      return matchName(fullMatcher, fullName)
    }

    private fun processNamesByPattern(
      base: ChooseByNameViewModel,
      names: Array<String?>,
      pattern: String,
      indicator: ProgressIndicator?,
      consumer: Consumer<in MatchResult?>,
      preferStartMatches: Boolean
    ) {
      val matcher: MinusculeMatcher = buildPatternMatcher(pattern, preferStartMatches)
      val processor = Processor { name: String? ->
        ProgressManager.checkCanceled()
        val result: MatchResult? = matches(base, pattern, matcher, name)
        if (result != null) {
          consumer.consume(result)
        }
        true
      }
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress<String?>(listOf(*names), indicator, processor)) {
        throw ProcessCanceledException()
      }
    }

    private fun convertToMatchingPattern(base: ChooseByNameViewModel, pattern: String): String {
      return addSearchAnywherePatternDecorationIfNeeded(base, removeModelSpecificMarkup(base.getModel(), pattern))
    }

    private fun addSearchAnywherePatternDecorationIfNeeded(base: ChooseByNameViewModel, pattern: String): String {
      var pattern = pattern
      var trimmedPattern: String? = null
      if (base.isSearchInAnyPlace() && !(pattern.trim { it <= ' ' }.also { trimmedPattern = it }).isEmpty() && trimmedPattern!!.length > 1) {
        pattern = "*$pattern"
      }
      return pattern
    }

    private fun removeModelSpecificMarkup(model: ChooseByNameModel, pattern: String): String {
      var pattern = pattern
      if (model is ContributorsBasedGotoByModel) {
        pattern = model.removeModelSpecificMarkup(pattern)
      }
      return pattern
    }

    @JvmStatic
    protected fun matches(
      base: ChooseByNameViewModel,
      pattern: String,
      matcher: MinusculeMatcher,
      name: String?
    ): MatchResult? {
      if (name == null) {
        return null
      }
      if (base.getModel() is CustomMatcherModel) {
        try {
          return if ((base.getModel() as CustomMatcherModel).matches(name, pattern)) MatchResult(name, 0, true) else null
        }
        catch (e: Exception) {
          LOG.info(e)
          return null // no matches appear valid result for "bad" pattern
        }
      }
      return matchName(matcher, name)
    }

    private fun matchName(matcher: MinusculeMatcher, name: String): MatchResult? {
      val fragments = matcher.matchingFragments(name)
      return if (fragments != null) MatchResult(name, matcher.matchingDegree(name, false, fragments),
                                                MinusculeMatcher.isStartMatch(fragments))
      else null
    }

    protected fun buildPatternMatcher(pattern: String, preferStartMatches: Boolean): MinusculeMatcher {
      var builder = NameUtil.buildMatcher(pattern).withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      if (preferStartMatches) {
        builder = builder.preferringStartMatches()
      }

      return builder.build()
    }
  }
}