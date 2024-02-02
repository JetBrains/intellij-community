// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.PerTierInstance
import org.jetbrains.annotations.ApiStatus

/**
 * A partition of [Feature]s that indicates that they could be either used or not used by an ML model.
 */
@ApiStatus.Internal
data class Usage<T>(
  val used: T,
  val notUsed: T,
)

/**
 * A partition of [Feature]s that indicates that they could be either declared (by [com.intellij.platform.ml.TierDescriptor]) or not.
 *
 * Used to process [com.intellij.platform.ml.ObsoleteTierDescriptor]'s partial descriptions.
 * It shall be removed when all obsolete descriptors will be transferred to the new API.
 */
@ApiStatus.Internal
data class Declaredness<T>(
  val declared: T,
  val nonDeclared: T
)

/**
 * There are two characteristics of a feature as for now: whether it is declared (statically, in a tier descriptor),
 * and whether it is used by the ML model.
 * So, this container creates a partition for each category of features.
 */
typealias DescriptionPartition = Declaredness<Usage<Set<Feature>>>

/**
 * A main tier has a description (that is used to run the ML model), and it also could contain analysis features
 */
@ApiStatus.Internal
data class MainTierScheme(
  val description: Set<FeatureDeclaration<*>>,
  val analysis: Set<FeatureDeclaration<*>>
)

/**
 * An additional tier is provided occasionally, and it has only description
 */
@ApiStatus.Internal
data class AdditionalTierScheme(
  val description: Set<FeatureDeclaration<*>>
)

/**
 * All the data, that a tier instance that has been described has
 */
@ApiStatus.Internal
data class DescribedTierData(
  val description: DescriptionPartition,
)

/**
 * All the data, that a tier instance that has been described and analyzed has
 */
@ApiStatus.Internal
data class AnalysedTierData(
  val description: DescriptionPartition,
  val analysis: Set<Feature>
)

/**
 * A template for a container that contains data of main tier instances, as well as additional instances.
 * A Level is a collection of tiers that were declared on the same depth of an [com.intellij.platform.ml.impl.MLTask]'s declaration,
 * plus additional tiers, declared by [com.intellij.platform.ml.impl.approach.LogDrivenModelInference.additionallyDescribedTiers]
 * on the corresponding level.
 */
@ApiStatus.Internal
data class Level<M, A>(val main: M, val additional: A)

typealias DescribedLevel = Level<PerTierInstance<DescribedTierData>, PerTierInstance<DescribedTierData>>

typealias AnalysedLevel = Level<PerTierInstance<AnalysedTierData>, PerTierInstance<DescribedTierData>>

/**
 * Tree-like ml session's structure.
 *
 * All trees leaves have the same depths.
 * The depth corresponds to the number of levels in an [com.intellij.platform.ml.impl.MLTask].
 * And the tree's structure is built by calling [com.intellij.platform.ml.NestableMLSession.createNestedSession].
 *
 * @param RootT Type of the data, that is stored in the root node.
 * @param LevelT Type of the data, that is stored in each tree's node.
 * @param PredictionT Type of the session's prediction.
 */
@ApiStatus.Internal
sealed interface SessionTree<RootT, LevelT, PredictionT> {
  /**
   * Data, that is stored in each tree's node.
   */
  val level: LevelT

  /**
   * Accepts the [visitor], calling the corresponding interface's function.
   */
  fun <T> accept(visitor: Visitor<RootT, LevelT, PredictionT, T>): T

  /**
   * Something that contains tree's root data.
   * There are two such classes: a [SolitaryLeaf], and [ComplexRoot].
   */
  sealed interface RootContainer<RootT, LevelT, PredictionT> : SessionTree<RootT, LevelT, PredictionT> {
    /**
     * Data, that is stored only in the tree's root.
     * ML model, for example.
     */
    val root: RootT
  }

  /**
   * Something that has nested nodes.
   * It could be either [ComplexRoot], or [Branching].
   * The number of children corresponds to number of calls of
   * [com.intellij.platform.ml.NestableMLSession.createNestedSession].
   */
  sealed interface ChildrenContainer<RootT, LevelT, PredictionT> : SessionTree<RootT, LevelT, PredictionT> {
    /**
     * All nested trees, that were built by calling
     * [com.intellij.platform.ml.NestableMLSession.createNestedSession] on this level.
     */
    val children: List<SessionTree<RootT, LevelT, PredictionT>>
  }

  /**
   * Something that contains session's prediction.
   * It is produced by [com.intellij.platform.ml.SinglePrediction], and the prediction could be either
   * produced or canceled, hence the [prediction] is nullable.
   */
  sealed interface PredictionContainer<RootT, LevelT, PredictionT> : SessionTree<RootT, LevelT, PredictionT> {
    val prediction: PredictionT?
  }

  /**
   * Corresponds to an ML task session's structure, that had only one level, and could not have been nested.
   * Hence, it contains root data and a prediction simultaneously.
   */
  data class SolitaryLeaf<RootT, LevelT, PredictionT>(
    override val root: RootT,
    override val level: LevelT,
    override val prediction: PredictionT?
  ) : RootContainer<RootT, LevelT, PredictionT>, PredictionContainer<RootT, LevelT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, LevelT, PredictionT, T>): T {
      return visitor.acceptSolitaryLeaf(this)
    }
  }

  /**
   * Corresponds to an ML task session's structure, that had more than one level.
   */
  data class ComplexRoot<RootT, LevelT, PredictionT>(
    override val root: RootT,
    override val level: LevelT,
    override val children: List<SessionTree<RootT, LevelT, PredictionT>>
  ) : RootContainer<RootT, LevelT, PredictionT>, ChildrenContainer<RootT, LevelT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, LevelT, PredictionT, T>): T = visitor.acceptRoot(this)
  }

  /**
   * Corresponds to a node in an ML task session's structure, that had more than one level.
   */
  data class Branching<RootT, LevelT, PredictionT>(
    override val level: LevelT,
    override val children: List<SessionTree<RootT, LevelT, PredictionT>>
  ) : SessionTree<RootT, LevelT, PredictionT>, ChildrenContainer<RootT, LevelT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, LevelT, PredictionT, T>): T = visitor.acceptBranching(this)
  }

  /**
   * Corresponds to a leaf in an ML task session's structure, that ad more than one level.
   */
  data class Leaf<RootT, LevelT, PredictionT>(
    override val level: LevelT,
    override val prediction: PredictionT?
  ) : SessionTree<RootT, LevelT, PredictionT>, PredictionContainer<RootT, LevelT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, LevelT, PredictionT, T>): T = visitor.acceptLeaf(this)
  }

  /**
   * Visits a [SessionTree]'s node.
   *
   * @param RootT Data's type, stored in the tree's root.
   * @param LevelT Data's type, stored in each node.
   * @param PredictionT Prediction's type in the ML session.
   * @param T Data type that is returned by the visitor.
   */
  interface Visitor<RootT, LevelT, PredictionT, out T> {
    fun acceptBranching(branching: Branching<RootT, LevelT, PredictionT>): T

    fun acceptLeaf(leaf: Leaf<RootT, LevelT, PredictionT>): T

    fun acceptRoot(root: ComplexRoot<RootT, LevelT, PredictionT>): T

    fun acceptSolitaryLeaf(solitaryLeaf: SolitaryLeaf<RootT, LevelT, PredictionT>): T
  }

  /**
   * Visits all tree's nodes on [levelIndex] depth.
   */
  abstract class LevelVisitor<RootT, PredictionT : Any> private constructor(
    private val levelIndex: Int,
    private val thisVisitorLevel: Int,
  ) : Visitor<RootT, DescribedLevel, PredictionT, Unit> {
    constructor(levelIndex: Int) : this(levelIndex, 0)

    private inner class DeeperLevelVisitor : LevelVisitor<RootT, PredictionT>(levelIndex, thisVisitorLevel + 1) {
      override fun visitLevel(level: DescribedLevel, levelRoot: SessionTree<RootT, DescribedLevel, PredictionT>) {
        this@LevelVisitor.visitLevel(level, levelRoot)
      }
    }

    private fun maybeVisitLevel(level: DescribedLevel,
                                levelRoot: SessionTree<RootT, DescribedLevel, PredictionT>): Boolean =
      if (levelIndex == thisVisitorLevel) {
        visitLevel(level, levelRoot)
        true
      }
      else false

    final override fun acceptBranching(branching: Branching<RootT, DescribedLevel, PredictionT>) {
      if (maybeVisitLevel(branching.level, branching)) return
      for (child in branching.children) {
        child.accept(DeeperLevelVisitor())
      }
    }

    final override fun acceptLeaf(leaf: Leaf<RootT, DescribedLevel, PredictionT>) {
      require(maybeVisitLevel(leaf.level, leaf)) {
        "The deepest level in the session tree is $thisVisitorLevel, given level $levelIndex does not exist"
      }
    }

    final override fun acceptRoot(root: ComplexRoot<RootT, DescribedLevel, PredictionT>) {
      if (maybeVisitLevel(root.level, root)) return
      for (child in root.children) {
        child.accept(DeeperLevelVisitor())
      }
    }

    final override fun acceptSolitaryLeaf(solitaryLeaf: SolitaryLeaf<RootT, DescribedLevel, PredictionT>) {
      require(maybeVisitLevel(solitaryLeaf.level, solitaryLeaf)) {
        "The only level in the session tree is $thisVisitorLevel, given level $levelIndex does not exist"
      }
    }

    abstract fun visitLevel(level: DescribedLevel, levelRoot: SessionTree<RootT, DescribedLevel, PredictionT>)
  }
}

typealias DescribedSessionTree<R, P> = SessionTree<R, DescribedLevel, P>

typealias DescribedChildrenContainer<R, P> = SessionTree.ChildrenContainer<R, DescribedLevel, P>

typealias DescribedRootContainer<R, P> = SessionTree.RootContainer<R, DescribedLevel, P>

typealias SessionAnalysis = Map<String, Set<Feature>>

typealias AnalysedSessionTree<P> = SessionTree<SessionAnalysis, AnalysedLevel, P>

typealias AnalysedRootContainer<P> = SessionTree.RootContainer<SessionAnalysis, AnalysedLevel, P>

val <R, P> DescribedSessionTree<R, P>.environment: Environment
  get() = Environment.of(this.level.main.keys)

val DescribedLevel.environment: Environment
  get() = Environment.of(this.main.keys)
