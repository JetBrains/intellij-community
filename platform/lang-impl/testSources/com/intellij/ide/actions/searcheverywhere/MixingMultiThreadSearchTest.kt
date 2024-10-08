// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase.assertNotNull
import com.intellij.testFramework.fixtures.BasePlatformTestCase.assertTrue
import com.intellij.util.Alarm
import com.intellij.util.Processor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.ListCellRenderer
import kotlin.Throws

private const val MORE_ITEM = "...MORE"
private const val MIXED_RESULTS_FEATURE = "search.everywhere.mixed.results"

@RunWith(com.intellij.testFramework.Parameterized::class)
class MixingMultiThreadSearchTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    val experiments = Experiments.getInstance()
    val mixedResultsWereEnabled = experiments.isFeatureEnabled(MIXED_RESULTS_FEATURE)
    experiments.setFeatureEnabled(MIXED_RESULTS_FEATURE, true)
    Disposer.register(testRootDisposable, Disposable { experiments.setFeatureEnabled(MIXED_RESULTS_FEATURE, mixedResultsWereEnabled) })
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testName: String? = null

  @JvmField
  @Parameterized.Parameter(1)
  var scenario: Scenario? = null

  @Test
  fun testScenario() {
    val scenario = requireNotNull(scenario)
    val collector = SearchResultsCollector()
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)
    val searcher = MixedResultsSearcher(collector,
                                        Executor { command -> alarm.addRequest(command, 0) },
                                        setOf(TrivialElementsEqualityProvider()))
    val indicator = searcher.search(scenario.contributorsAndLimits, "tst")

    try {
      collector.awaitFinish(1000)
    }
    catch (_: TimeoutException) {
      fail("Search timeout exceeded")
    }
    catch (_: InterruptedException) {
    }
    finally {
      indicator.cancel()
    }

    val expectedValues = scenario.results.keys.map { collector.getContributorValues(it) }
    assertContainsOrdered(scenario.results.values, expectedValues)
    collector.clear()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Any> = emptyList()

    @Suppress("unused")
    @JvmStatic
    @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
    fun testNames(klass: Class<*>): List<Array<Any>> = listOf(
      arrayOf("test_simple_without_collisions", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0, "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9",
                          "item1_10", "item1_11", "item1_12", "item1_13", "item1_14", "item1_15") to 12,
          TestContributor("test2", 0, "item2_1", "item2_2", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8", "item2_9",
                          "item2_10", "item2_11", "item2_12") to 10,
          TestContributor("test3", 0, "item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8") to 10,
          TestContributor("test4", 0, "item4_1", "item4_2", "item4_3", "item4_4", "item4_5", "item4_6", "item4_7", "item4_8", "item4_9",
                          "item4_10", "item4_11", "item4_12", "item4_13") to 11,
          TestContributor("test5", 0) to 10,
          TestContributor("test6", 0, "item6_1", "item6_2", "item6_3", "item6_4", "item6_5", "item6_6", "item6_7", "item6_8", "item6_9",
                          "item6_10", "item6_11", "item6_12", "item6_13") to 10,
          TestContributor("test7", 0, "item7_1", "item7_2", "item7_3", "item7_4", "item7_5", "item7_6", "item7_7", "item7_8", "item7_9",
                          "item7_10") to 10,
          TestContributor("test8", 0, "item8_1", "item8_2", "item8_3", "item8_4", "item8_5") to 10,
          TestContributor("test9", 0, "item9_1", "item9_2", "item9_3", "item9_4", "item9_5") to 3,
          TestContributor("test10", 0, "item10_1", "item10_2", "item10_3", "item10_4", "item10_5") to 5
        ),
        results = mapOf(
          "test1" to listOf("item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9", "item1_10",
                            "item1_11", "item1_12", MORE_ITEM),
          "test2" to listOf("item2_1", "item2_2", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8", "item2_9", "item2_10", MORE_ITEM),
          "test3" to listOf("item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8"),
          "test4" to listOf("item4_1", "item4_2", "item4_3", "item4_4", "item4_5", "item4_6", "item4_7", "item4_8", "item4_9", "item4_10",
                            "item4_11", MORE_ITEM),
          "test5" to emptyList<String>(),
          "test6" to listOf("item6_1", "item6_2", "item6_3", "item6_4", "item6_5", "item6_6", "item6_7", "item6_8", "item6_9", "item6_10", MORE_ITEM),
          "test7" to listOf("item7_1", "item7_2", "item7_3", "item7_4", "item7_5", "item7_6", "item7_7", "item7_8", "item7_9", "item7_10"),
          "test8" to listOf("item8_1", "item8_2", "item8_3", "item8_4", "item8_5"),
          "test9" to listOf("item9_1", "item9_2", "item9_3", MORE_ITEM),
          "test10" to listOf("item10_1", "item10_2", "item10_3", "item10_4", "item10_5")
        ))
      ),
      arrayOf("test_simple_without_MORE_items", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0, "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9",
                          "item1_10") to 10,
          TestContributor("test2", 0, "item2_1", "item2_2", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8", "item2_9",
                          "item2_10", "item2_11", "item2_12") to 20,
          TestContributor("test3", 0, "item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8") to 10
        ),
        results = mapOf(
          "test1" to listOf("item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9", "item1_10"),
          "test2" to listOf("item2_1", "item2_2", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8", "item2_9", "item2_10",
                            "item2_11", "item2_12"),
          "test3" to listOf("item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8")
        ))
      ),
      arrayOf("test_empty_results", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0) to 10,
          TestContributor("test2", 0) to 10,
          TestContributor("test3", 0) to 10,
          TestContributor("test4", 0) to 10,
          TestContributor("test5", 0) to 10
        ),
        results = mapOf(
          "test1" to emptyList<String>(),
          "test2" to emptyList<String>(),
          "test3" to emptyList<String>(),
          "test4" to emptyList<String>(),
          "test5" to emptyList<String>()
        ))
      ),
      arrayOf("test_one_contributor", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0, "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9",
                          "item1_10", "item1_11", "item1_12", "item1_13", "item1_14", "item1_15") to 10
        ),
        results = mapOf(
          "test1" to listOf("item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9", "item1_10", MORE_ITEM)
        ))
      ),
      arrayOf("test_one_contributor_with_no_MORE_item", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0, "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9",
                          "item1_10") to 10
        ),
        results = mapOf(
          "test1" to listOf("item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9", "item1_10")
        ))
      ),
      arrayOf("test_one_contributor_with_empty_results", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0) to 10
        ),
        results = mapOf(
          "test1" to emptyList<String>()
        ))
      ),
      arrayOf("test_collisions_scenario", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 0, "item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9",
                          "item1_10", "item1_11", "item1_12", "item1_13", "item1_14", "item1_15") to 12,
          TestContributor("test2", 10, "item2_1", "item2_2", "duplicateItem1", "duplicateItem2", "item2_3", "item2_4", "item2_5", "item2_6",
                          "item2_7", "item2_8", "item2_9", "item2_10", "item2_11", "item2_12") to 10,
          TestContributor("test3", 8, "item3_1", "item3_2", "item3_3", "item3_4", "duplicateItem1", "item3_5", "item3_6", "item3_7", "item3_8",
                          "duplicateItem2", "duplicateItem3") to 10,
          TestContributor("test4", 15, "item4_1", "item4_2", "duplicateItem2", "duplicateItem3", "item4_3", "item4_4", "item4_5", "item4_6",
                          "item4_7", "item4_8", "item4_9") to 10,
          TestContributor("test6", 20, "item6_1", "item6_2", "item6_3", "item6_4", "duplicateItem3", "item6_5", "item6_6", "item6_7", "item6_8",
                          "item6_9", "item6_10", "item6_11", "duplicateItem4", "item6_12", "item6_13") to 10,
          TestContributor("test7", 5, "item7_1", "item7_2", "duplicateItem3", "item7_3", "item7_4", "item7_5", "item7_6", "item7_7", "item7_8",
                          "item7_9", "item7_10") to 10,
          TestContributor("test8", 10, "item8_1", "item8_2", "item8_3", "item8_4", "item8_5", "duplicateItem4") to 10,
          TestContributor("test9", 15, "item9_1", "item9_2", "item9_3", "item9_4", "duplicateItem5", "duplicateItem6") to 5,
          TestContributor("test10", 10, "item10_1", "item10_2", "item10_3", "item10_4", "duplicateItem5", "duplicateItem6") to 5
        ),
        results = mapOf(
          "test1" to listOf("item1_1", "item1_2", "item1_3", "item1_4", "item1_5", "item1_6", "item1_7", "item1_8", "item1_9", "item1_10", "item1_11",
                            "item1_12", MORE_ITEM),
          "test2" to listOf("item2_1", "item2_2", "duplicateItem1", "item2_3", "item2_4", "item2_5", "item2_6", "item2_7", "item2_8", "item2_9", MORE_ITEM),
          "test3" to listOf("item3_1", "item3_2", "item3_3", "item3_4", "item3_5", "item3_6", "item3_7", "item3_8"),
          "test4" to listOf("item4_1", "item4_2", "duplicateItem2", "item4_3", "item4_4", "item4_5", "item4_6", "item4_7", "item4_8", "item4_9"),
          "test6" to listOf("item6_1", "item6_2", "item6_3", "item6_4", "duplicateItem3", "item6_5", "item6_6", "item6_7", "item6_8", "item6_9", MORE_ITEM),
          "test7" to listOf("item7_1", "item7_2", "item7_3", "item7_4", "item7_5", "item7_6", "item7_7", "item7_8", "item7_9", "item7_10"),
          "test8" to listOf("item8_1", "item8_2", "item8_3", "item8_4", "item8_5", "duplicateItem4"),
          "test9" to listOf("item9_1", "item9_2", "item9_3", "item9_4", "duplicateItem5", MORE_ITEM),
          "test10" to listOf("item10_1", "item10_2", "item10_3", "item10_4", "duplicateItem6")
        ))
      ),
      arrayOf("test_only_collisions", Scenario(
        contributorsAndLimits = mapOf(
          TestContributor("test1", 10, "item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8", "item9", "item10", "item11", "item12") to 5,
          TestContributor("test2", 9, "item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8", "item9", "item10", "item11", "item12") to 5,
          TestContributor("test3", 8, "item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8", "item9", "item10", "item11", "item12") to 5,
          TestContributor("test4", 7, "item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8", "item9", "item10", "item11", "item12") to 5,
          TestContributor("test5", 6, "item1", "item2", "item3", "item4", "item5", "item6", "item7", "item8", "item9", "item10", "item11", "item12") to 5
        ),
        results = mapOf(
          "test1" to listOf("item1", "item2", "item3", "item4", "item5", MORE_ITEM),
          "test2" to listOf("item6", "item7", "item8", "item9", "item10", MORE_ITEM),
          "test3" to listOf("item11", "item12"),
          "test4" to emptyList<String>(),
          "test5" to emptyList<String>()
        ))
      ),
    )
  }
}

class SearchResultsCollector : SearchListener {
  private val myMap: MutableMap<String, MutableList<String>> = ConcurrentHashMap<String, MutableList<String>>()
  private val myFinished = AtomicBoolean(false)
  private val myPhaser = Phaser(2)

  fun getContributorValues(contributorId: String): List<String> {
    return myMap.getOrElse(contributorId) { mutableListOf() }
  }

  @Throws(TimeoutException::class, InterruptedException::class)
  fun awaitFinish(timeout: Long) {
    val phase = myPhaser.arrive()
    myPhaser.awaitAdvanceInterruptibly(phase, timeout, TimeUnit.MILLISECONDS)
  }

  fun clear() {
    myMap.clear()
    myFinished.set(false)
  }

  override fun elementsAdded(added: List<SearchEverywhereFoundElementInfo>) {
    added.forEach { info ->
      val list = myMap.computeIfAbsent(info.getContributor().getSearchProviderId()) { mutableListOf() }
      list.add(info.getElement() as String)
    }
  }

  override fun elementsRemoved(removed: List<SearchEverywhereFoundElementInfo>) {
    removed.forEach { info ->
      val list = myMap[info.getContributor().getSearchProviderId()]
      assertNotNull("Trying to remove object, that wasn't added", list)
      list!!.remove(info.getElement())
    }
  }

  override fun searchFinished(hasMoreContributors: MutableMap<SearchEverywhereContributor<*>, Boolean>) {
    hasMoreContributors.entries
      .filter { it.value }
      .map { it.key }
      .forEach {
        val list = myMap[it.getSearchProviderId()]
        assertNotNull("If section has MORE item it cannot be empty", list)
        list!!.add(MORE_ITEM)
      }

    val set = myFinished.compareAndSet(false, true)
    assertTrue("More than one finish event", set)

    myPhaser.arrive()
  }

  override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}
  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}
  override fun searchStarted(pattern: String, contributors: Collection<SearchEverywhereContributor<*>>) {}
}

class Scenario(val contributorsAndLimits: Map<SearchEverywhereContributor<Any>, Int>, val results: Map<String, List<String>>)

class TestContributor(private val id: String, private val fixedPriority: Int, private vararg val items: String) : SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = id
  override fun getGroupName(): String = id
  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = false
  override fun getElementPriority(element: Any, searchPattern: String): Int = fixedPriority
  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Any?>) {
    for (item in items) {
      if (!consumer.process(item)) return
    }
  }

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean = false
  override fun getElementsRenderer(): ListCellRenderer<in Any?> = throw UnsupportedOperationException()
}
