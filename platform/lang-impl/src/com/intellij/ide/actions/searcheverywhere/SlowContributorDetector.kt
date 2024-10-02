// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.truncate

private val LOG = Logger.getInstance(SlowContributorDetector::class.java)

private const val EMPTY_RESULT_THRESHOLD = 50
private const val WARNING_THRESHOLD = 2000

@ApiStatus.Internal
class SlowContributorDetector: SearchListener {

  private var startTimestamp: Long? = null
  private val finishedContributors = HashMap<String, Long>()
  private val contributorsWithEvents = HashSet<String>()

  override fun searchStarted(pattern: String,
                             contributors: Collection<SearchEverywhereContributor<*>>): Unit = restart()

  override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo>): Unit = updateContributorsWithEvents(list)

  override fun elementsRemoved(list: List<SearchEverywhereFoundElementInfo>): Unit = updateContributorsWithEvents(list)

  private fun updateContributorsWithEvents(list: Collection<SearchEverywhereFoundElementInfo>) {
    list.mapNotNull { it.contributor?.searchProviderId }.let { contributorsWithEvents.addAll(it) }
  }

  override fun contributorWaits(contributor: SearchEverywhereContributor<*>): Unit = logContributorFinished(contributor)

  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean): Unit = logContributorFinished(contributor)

  override fun searchFinished(hasMoreContributors: Map<SearchEverywhereContributor<*>, Boolean>) {
    hasMoreContributors.forEach { (contributor, _) -> logContributorFinished(contributor) }
    findSlow()
    if (LOG.isDebugEnabled) printDebugInfo()
  }

  private fun printDebugInfo() {
    val values = mutableListOf<String>()
    values.addAll(finishedContributors.map { entry -> "${entry.key}: ${entry.value}" })

    val delays = getFilteredDelays()
    val sigmaEdge = calculateSigmaEdge(delays)
    val twoSigmaEdge = calculateTwoSigmaEdge(delays)
    val percentileEdge = calculatePercentileEdge(delays)
    values.add("Delays: $delays")
    values.add("Sigma edge: $sigmaEdge")
    values.add("2Sigma edge: $twoSigmaEdge")
    values.add("Percentile edge: $percentileEdge")

    LOG.debugValues("Contributors performance stats", values)
  }

  private fun logContributorFinished(contributor: SearchEverywhereContributor<*>) {
    if (finishedContributors.containsKey(contributor.searchProviderId)) return
    if (!EssentialContributor.checkEssential(contributor)) return //don't worry about contributors which are not essential
    finishedContributors[contributor.searchProviderId] = System.currentTimeMillis() - startTimestamp!!
  }

  private fun restart() {
    startTimestamp = System.currentTimeMillis()
    finishedContributors.clear()
    contributorsWithEvents.clear()
  }

  private fun findSlow() {
    val delays = getFilteredDelays()
    if (delays.isEmpty()) return

    val edge = calculateSigmaEdge(delays)
    finishedContributors.forEach { (id, delay) -> if (delay >= edge && delay >= WARNING_THRESHOLD) reportSlowContributor(id, delay) }
  }

  private fun getFilteredDelays(): Collection<Long> = finishedContributors.mapNotNull { (key, value) ->
    if (contributorsWithEvents.contains(key) || value >= EMPTY_RESULT_THRESHOLD) value else null
  }


  private fun reportSlowContributor(id: String, delay: Long) {
    LOG.warn("Contributor [$id] is too slow (took $delay ms to finish). But it is marked as EssentialContributor and can slow down the search process")
  }

  private fun calculatePercentileEdge(delays: Collection<Long>): Long {
    val list = delays.sorted()
    val q1 = calcPercentile(list, 0.25)
    val q3 = calcPercentile(list, 0.75)
    val qRange = (q3 - q1) * 3

    return (q3 + qRange).toLong()
  }

  private fun calculateTwoSigmaEdge(delays: Collection<Long>): Long {
    return delays.average().toLong() + 2 * calcSigma(delays)
  }

  private fun calculateSigmaEdge(delays: Collection<Long>): Long {
    return delays.average().toLong() + calcSigma(delays)
  }

  private fun calcPercentile(list: List<Long>, percentile: Double): Double {
    if (list.isEmpty()) return 0.0

    val num = list.size * percentile
    if (num == truncate(num)) {
      val index = num.toInt()
      return if (index == 0) 0.0 else (list[index] + list[index - 1]) / 2.0
    }
    else {
      val index = ceil(num).toInt()
      return if (index >= list.size) list.last() + 1.0 else list[index].toDouble()
    }
  }

  private fun calcSigma(delays: Collection<Long>): Long {
    val avg = delays.average()
    val sigma = sqrt(delays.sumOf { (it - avg).pow(2) } / (delays.size))
    return sigma.toLong()
  }

}