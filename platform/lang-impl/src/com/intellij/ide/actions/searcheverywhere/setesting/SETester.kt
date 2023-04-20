// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.setesting

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Convertor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SETester(private val contributors: List<SearchEverywhereContributor<*>>, private val searchPattern: String, private val elementsLimit: Int) {

  fun test(indicator: ProgressIndicator) : Map<SearchEverywhereContributor<*>, List<Long>> {
    val collector = ResultsCollector(elementsLimit, contributors)
    val latch = CountDownLatch(contributors.size)
    collector.start()
    contributors.forEach { contributor ->
      ApplicationManager.getApplication().executeOnPooledThread(createTask(contributor, latch, collector, indicator))
    }

    var finished = false
    while(indicator.isRunning && !finished) finished = latch.await(100, TimeUnit.MILLISECONDS)
    collector.finish()

    return collector.getResults()
  }

  private fun createTask(contributor: SearchEverywhereContributor<*>, latch: CountDownLatch, collector: ResultsCollector, indicator: ProgressIndicator) = Runnable {
    var repeat: Boolean
    do {
      val wrapperIndicator = SensitiveProgressWrapper(indicator)
      contributor.fetchElements(searchPattern, wrapperIndicator, Processor { collector.collect(contributor) })
      repeat = !indicator.isCanceled && wrapperIndicator.isCanceled
    }
    while(repeat)
    latch.countDown()
  }
}

private class ResultsCollector(val elementsLimit: Int, contributors: List<SearchEverywhereContributor<*>>) {
  private var startTime : Long? = null
  private var finishTime : Long? = null
  //private val timingsMap = concurrentMapOf<SearchEverywhereContributor<*>, MutableList<Long>>()
  private val timingsMap : Map<SearchEverywhereContributor<*>, MutableList<Long>> = ContainerUtil.newMapFromKeys(contributors.listIterator(), Convertor { mutableListOf() })

  fun start() {
    startTime = System.currentTimeMillis()
  }

  fun finish() {
    startTime ?: throw IllegalStateException("Collecting was not started")
    finishTime = System.currentTimeMillis() - startTime!!
  }

  fun collect(contributor: SearchEverywhereContributor<*>): Boolean {
    startTime ?: throw IllegalStateException("Collecting was not started")

    val list = timingsMap[contributor] ?: throw IllegalStateException("No backet for this contributor")
    if (list.size >= elementsLimit) return false
    list.add(System.currentTimeMillis() - startTime!!)
    return true
  }

  fun getResults() : Map<SearchEverywhereContributor<*>, List<Long>> {
    startTime ?: throw IllegalStateException("Collecting was not started")
    finishTime ?: throw IllegalStateException("Collecting was not finished")

    return timingsMap
  }
}
