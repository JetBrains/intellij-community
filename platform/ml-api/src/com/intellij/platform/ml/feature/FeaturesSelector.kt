// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.feature

import com.intellij.platform.ml.PerTier
import org.jetbrains.annotations.ApiStatus

/**
 * Determines a set of features, and can tell if a set of features is "complete" or not.
 * The Meaning of the "completeness" depends on the selector's usage.
 *
 * Selectors are used to determine if there are enough features to run an ML model,
 * and to not compute redundant features, the ones that will not be used by the ML model,
 * and are not desired to be logged.
 *
 * Two types of instances have the power to select features:
 *  - [com.intellij.platform.ml.MLModel] to tell which features are known.
 *  - [com.intellij.platform.ml.LogDrivenModelInference] to tell which features are not known by the ML model,
 *  but they are still must be computed and then logged.
 */
@ApiStatus.Internal
interface FeatureSelector {
  /**
   * @param availableFeatures A set of features that the selection will be built from.
   *
   * @return A set of features that are 'selected' and a completeness marker
   * [Selection.Incomplete] is returned if there are some more features that are 'selected',
   * but they are not present among [availableFeatures].
   * [Selection.Complete] is returned otherwise.
   */
  fun select(availableFeatures: Set<FeatureDeclaration<*>>): Selection

  /**
   * @param featureDeclaration A single feature, that needs to be selected.
   *
   * @return If the feature belongs to the determined set of features.
   */
  fun select(featureDeclaration: FeatureDeclaration<*>): Boolean {
    return select(setOf(featureDeclaration)).selectedFeatures.isNotEmpty()
  }

  sealed class Selection(val selectedFeatures: Set<FeatureDeclaration<*>>) {
    class Complete(selectedFeatures: Set<FeatureDeclaration<*>>) : Selection(selectedFeatures)

    open class Incomplete(selectedFeatures: Set<FeatureDeclaration<*>>) : Selection(selectedFeatures) {
      open val details: String = "Incomplete selection, only these were selected: $selectedFeatures"
    }

    companion object {
      val NOTHING = Complete(emptySet())
    }
  }

  companion object {
    val NOTHING = object : FeatureSelector {
      override fun select(availableFeatures: Set<FeatureDeclaration<*>>): Selection = Selection.NOTHING
    }

    val EVERYTHING = object : FeatureSelector {
      override fun select(availableFeatures: Set<FeatureDeclaration<*>>): Selection = Selection.Complete(availableFeatures)
    }

    infix fun FeatureSelector.or(other: FeatureFilter): FeatureSelector {
      return object : FeatureSelector {
        override fun select(availableFeatures: Set<FeatureDeclaration<*>>): Selection {
          val thisSelection = this@or.select(availableFeatures)
          val otherSelection = other.accept(availableFeatures)
          val joinedSelection = thisSelection.selectedFeatures + otherSelection
          return if (thisSelection is Selection.Incomplete) {
            object : Selection.Incomplete(joinedSelection) {
              override val details: String
                get() = thisSelection.details
            }
          }
          else
            Selection.Complete(joinedSelection)
        }
      }
    }

    infix fun PerTier<FeatureSelector>.or(other: PerTier<FeatureFilter>): PerTier<FeatureSelector> {
      require(this.keys == other.keys)
      return keys.associateWith { this.getValue(it) or other.getValue(it) }
    }

    fun FeatureSelector.asFilter() = object : FeatureFilter {
      override fun accept(featureDeclarations: Set<FeatureDeclaration<*>>): Set<FeatureDeclaration<*>> {
        val selection = this@asFilter.select(featureDeclarations)
        return selection.selectedFeatures
      }

      override fun accept(featureDeclaration: FeatureDeclaration<*>): Boolean {
        return this@asFilter.select(featureDeclaration)
      }
    }
  }
}
