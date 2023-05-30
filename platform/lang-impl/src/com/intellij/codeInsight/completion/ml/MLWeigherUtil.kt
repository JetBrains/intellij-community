// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MLWeigherUtil {
  const val ML_COMPLETION_WEIGHER_ID: String = "ml_weigh"

  @JvmStatic
  fun findMLWeigher(location: CompletionLocation): LookupElementWeigher? {
    val weigher = WeigherExtensionPoint.EP.extensionList
                    .firstOrNull { ML_COMPLETION_WEIGHER_ID == it.id && it.key == "completion" }
                    ?.instance ?: return null
    return MLWeigherWrapper(weigher, location)
  }

  @JvmStatic
  fun addWeighersToNonDefaultSorter(sorter: CompletionSorter, location: CompletionLocation, vararg weigherIds: String): CompletionSorter {
    // from BaseCompletionService.defaultSorter
    var result = sorter
    for (weigher in WeighingService.getWeighers(CompletionService.RELEVANCE_KEY)) {
      val id = weigher.toString()
      if (weigherIds.contains(id)) {
        result = result.weigh(object : LookupElementWeigher(id, true, false) {
          override fun weigh(element: LookupElement): Comparable<*> {
            val weigh = weigher.weigh(element, location) ?: return DummyWeigherComparableDelegate.EMPTY
            return DummyWeigherComparableDelegate(weigh)
          }
        })
      }
    }
    return result
  }

  private class MLWeigherWrapper(private val mlWeigher: Weigher<Any, Any>, private val location: CompletionLocation)
    : LookupElementWeigher(ML_COMPLETION_WEIGHER_ID) {
    override fun weigh(element: LookupElement): Comparable<*>? {
      return mlWeigher.weigh(element, location)
    }
  }

  @ApiStatus.Internal
  fun extractWeightsOrNull(comparable: Any): Map<String, Any>? {
    return when (comparable) {
      is DummyWeigherComparableDelegate -> comparable.getWeights()
      is WeighingComparable<*, *> -> comparable.weights
      else -> null
    }
  }
}

private class DummyWeigherComparableDelegate(private val weigh: Comparable<*>?)
  : Comparable<DummyWeigherComparableDelegate>, ForceableComparable {

  companion object {
    val EMPTY = DummyWeigherComparableDelegate(null)
  }

  override fun force() {
    if (weigh is ForceableComparable) {
      (weigh as ForceableComparable).force()
    }
  }

  override operator fun compareTo(other: DummyWeigherComparableDelegate): Int {
    return 0
  }

  fun getWeights(): Map<String, Any>? {
    weigh ?: return emptyMap()

    return if (weigh is WeighingComparable<*, *>) weigh.weights else null
  }

  override fun toString(): String {
    return weigh?.toString() ?: ""
  }
}
