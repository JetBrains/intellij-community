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
data class AnalysedTierScheme(
  val description: Set<FeatureDeclaration<*>>,
  val analysis: Set<FeatureDeclaration<*>>
)

/**
 * An additional tier is provided occasionally, and it has only description
 */
@ApiStatus.Internal
data class DescribedTierScheme(
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
 * Data that contains each level of a [SessionTree].
 *
 * @param M Type of data that is stored for 'main' instances.
 * The additional instances always contain [DescribedTierData], but the main instances contain [DescribedTierData] after they have
 * been described, and [AnalysedTierData] after they have been analyzed.
 * @param mainInstances The main tiers' data the task (either [DescribedTierData], or [AnalysedTierData])
 * @param additionalInstances The additional tiers' data of the task (only [DescribedTierData], as additional levels are not analyzed).
 */
@ApiStatus.Internal
data class LevelData<M>(
  val mainInstances: PerTierInstance<M>,
  val additionalInstances: PerTierInstance<DescribedTierData>,
  val callParameters: Environment
)

typealias DescribedLevel = LevelData<DescribedTierData>

typealias AnalysedLevel = LevelData<AnalysedTierData>

/**
 * Tree-like ml session's structure.
 *
 * All trees leaves have the same depths.
 * The depth corresponds to the number of levels in an [com.intellij.platform.ml.impl.MLTask].
 * And the tree's structure is built by calling [com.intellij.platform.ml.NestableMLSession.createNestedSession].
 *
 * @param RootT Type of the data, that is stored in the root node.
 * @param MainT Type of the data that is stored for each main tier instance. For additional instances, always [DescribedTierData]
 * is stored.
 * @param PredictionT Type of the session's prediction.
 */
@ApiStatus.Internal
sealed interface SessionTree<RootT, MainT, PredictionT> {
  /**
   * Data, that is stored in each tree's node.
   */
  val levelData: LevelData<MainT>

  /**
   * Accepts the [visitor], calling the corresponding interface's function.
   *
   * see [Visitor], [LevelVisitor]
   */
  fun <T> accept(visitor: Visitor<RootT, MainT, PredictionT, T>): T

  /**
   * Something that contains tree's root data.
   * There are two such classes: a [SolitaryLeaf], and [ComplexRoot].
   */
  sealed interface RootContainer<RootT, MainT, PredictionT> : SessionTree<RootT, MainT, PredictionT> {
    /**
     * Data, that is stored only in the tree's root.
     * ML model, for example.
     */
    val rootData: RootT
  }

  /**
   * Something that has nested nodes.
   * It could be either [ComplexRoot], or [Branching].
   * The number of children corresponds to number of calls of
   * [com.intellij.platform.ml.NestableMLSession.createNestedSession].
   */
  sealed interface ChildrenContainer<RootT, MainT, PredictionT> : SessionTree<RootT, MainT, PredictionT> {
    /**
     * All nested trees, that were built by calling
     * [com.intellij.platform.ml.NestableMLSession.createNestedSession] on this level.
     */
    val children: List<SessionTree<RootT, MainT, PredictionT>>
  }

  /**
   * Something that contains session's prediction.
   * It is produced by [com.intellij.platform.ml.SinglePrediction], and the prediction could be either
   * produced or canceled, hence the [prediction] is nullable.
   */
  sealed interface PredictionContainer<RootT, MainT, PredictionT> : SessionTree<RootT, MainT, PredictionT> {
    val prediction: PredictionT?
  }

  /**
   * Corresponds to an ML task session's structure, that had only one level, and could not have been nested.
   * Hence, it contains root data and a prediction simultaneously.
   */
  data class SolitaryLeaf<RootT, MainT, PredictionT>(
    override val rootData: RootT,
    override val levelData: LevelData<MainT>,
    override val prediction: PredictionT?
  ) : RootContainer<RootT, MainT, PredictionT>, PredictionContainer<RootT, MainT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, MainT, PredictionT, T>): T {
      return visitor.acceptSolitaryLeaf(this)
    }
  }

  /**
   * Corresponds to an ML task session's structure, that had more than one level.
   */
  data class ComplexRoot<RootT, MainT, PredictionT>(
    override val rootData: RootT,
    override val levelData: LevelData<MainT>,
    override val children: List<SessionTree<RootT, MainT, PredictionT>>
  ) : RootContainer<RootT, MainT, PredictionT>, ChildrenContainer<RootT, MainT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, MainT, PredictionT, T>): T = visitor.acceptComplexRoot(this)
  }

  /**
   * Corresponds to a node in an ML task session's structure, that had more than one level.
   */
  data class Branching<RootT, MainT, PredictionT>(
    override val levelData: LevelData<MainT>,
    override val children: List<SessionTree<RootT, MainT, PredictionT>>
  ) : SessionTree<RootT, MainT, PredictionT>, ChildrenContainer<RootT, MainT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, MainT, PredictionT, T>): T = visitor.acceptBranching(this)
  }

  /**
   * Corresponds to a leaf in an ML task session's structure, that ad more than one level.
   */
  data class Leaf<RootT, MainT, PredictionT>(
    override val levelData: LevelData<MainT>,
    override val prediction: PredictionT?
  ) : SessionTree<RootT, MainT, PredictionT>, PredictionContainer<RootT, MainT, PredictionT> {
    override fun <T> accept(visitor: Visitor<RootT, MainT, PredictionT, T>): T = visitor.acceptLeaf(this)
  }

  /**
   * Visits a [SessionTree]'s node.
   *
   * @param RootT Data's type, stored in the tree's root.
   * @param MainT Type of data, contained with each main tier instance.
   * @param PredictionT Prediction's type in the ML session.
   * @param T Data type that is returned by the visitor.
   */
  interface Visitor<RootT, MainT, PredictionT, out T> {
    fun acceptBranching(branching: Branching<RootT, MainT, PredictionT>): T

    fun acceptLeaf(leaf: Leaf<RootT, MainT, PredictionT>): T

    fun acceptComplexRoot(root: ComplexRoot<RootT, MainT, PredictionT>): T

    fun acceptSolitaryLeaf(solitaryLeaf: SolitaryLeaf<RootT, MainT, PredictionT>): T

    interface Default<ModelT, MainT, PredictionT> : Visitor<ModelT, MainT, PredictionT, Unit> {
      override fun acceptBranching(branching: Branching<ModelT, MainT, PredictionT>) = Unit

      override fun acceptLeaf(leaf: Leaf<ModelT, MainT, PredictionT>) = Unit

      override fun acceptComplexRoot(root: ComplexRoot<ModelT, MainT, PredictionT>) = Unit

      override fun acceptSolitaryLeaf(solitaryLeaf: SolitaryLeaf<ModelT, MainT, PredictionT>) = Unit
    }
  }

  /**
   * Visits all tree's nodes on [levelIndex] depth.
   *
   * [levelIndex] is zero for the permanent level.
   * It must be less than the number of levels in the corresponding task.
   */
  abstract class LevelVisitor<RootT, MainT, PredictionT : Any> private constructor(
    private val levelIndex: Int,
    private val thisVisitorLevel: Int,
  ) : Visitor<RootT, MainT, PredictionT, Unit> {
    constructor(levelIndex: Int) : this(levelIndex, 0)

    private inner class DeeperLevelVisitor : LevelVisitor<RootT, MainT, PredictionT>(levelIndex, thisVisitorLevel + 1) {
      override fun visitLevel(level: LevelData<MainT>, levelRoot: SessionTree<RootT, MainT, PredictionT>) {
        this@LevelVisitor.visitLevel(level, levelRoot)
      }
    }

    private fun maybeVisitLevel(level: LevelData<MainT>,
                                levelRoot: SessionTree<RootT, MainT, PredictionT>): Boolean =
      if (levelIndex == thisVisitorLevel) {
        visitLevel(level, levelRoot)
        true
      }
      else false

    final override fun acceptBranching(branching: Branching<RootT, MainT, PredictionT>) {
      if (maybeVisitLevel(branching.levelData, branching)) return
      for (child in branching.children) {
        child.accept(DeeperLevelVisitor())
      }
    }

    final override fun acceptLeaf(leaf: Leaf<RootT, MainT, PredictionT>) {
      require(maybeVisitLevel(leaf.levelData, leaf)) {
        "The deepest level in the session tree is $thisVisitorLevel, given level $levelIndex does not exist"
      }
    }

    final override fun acceptComplexRoot(root: ComplexRoot<RootT, MainT, PredictionT>) {
      if (maybeVisitLevel(root.levelData, root)) return
      for (child in root.children) {
        child.accept(DeeperLevelVisitor())
      }
    }

    final override fun acceptSolitaryLeaf(solitaryLeaf: SolitaryLeaf<RootT, MainT, PredictionT>) {
      require(maybeVisitLevel(solitaryLeaf.levelData, solitaryLeaf)) {
        "The only level in the session tree is $thisVisitorLevel, given level $levelIndex does not exist"
      }
    }

    abstract fun visitLevel(level: LevelData<MainT>, levelRoot: SessionTree<RootT, MainT, PredictionT>)
  }
}

typealias DescribedSessionTree<R, P> = SessionTree<R, DescribedTierData, P>

typealias DescribedChildrenContainer<R, P> = SessionTree.ChildrenContainer<R, DescribedTierData, P>

typealias DescribedRootContainer<R, P> = SessionTree.RootContainer<R, DescribedTierData, P>

typealias AnalysedSessionTree<P> = SessionTree<Unit, AnalysedTierData, P>

typealias AnalysedRootContainer<P> = SessionTree.RootContainer<Unit, AnalysedTierData, P>

internal val <R, P> DescribedSessionTree<R, P>.environment: Environment
  get() = Environment.of(this.levelData.mainInstances.keys)

internal val <T> LevelData<T>.environment: Environment
  get() = Environment.of(this.mainInstances.keys)

@get:ApiStatus.Internal
val <RootT, MainT, PredictionT> SessionTree<RootT, MainT, PredictionT>.predictions: List<SessionTree.PredictionContainer<RootT, MainT, PredictionT>>
  get() {
    val predictions: MutableList<SessionTree.PredictionContainer<RootT, MainT, PredictionT>> = mutableListOf()
    accept(object : SessionTree.Visitor<RootT, MainT, PredictionT, Unit> {
      override fun acceptLeaf(leaf: SessionTree.Leaf<RootT, MainT, PredictionT>) {
        predictions += leaf
      }

      override fun acceptBranching(branching: SessionTree.Branching<RootT, MainT, PredictionT>) {
        branching.children.forEach { child -> child.accept(this) }
      }

      override fun acceptComplexRoot(root: SessionTree.ComplexRoot<RootT, MainT, PredictionT>) {
        root.children.forEach { child -> child.accept(this) }
      }

      override fun acceptSolitaryLeaf(solitaryLeaf: SessionTree.SolitaryLeaf<RootT, MainT, PredictionT>) {
        predictions += solitaryLeaf
      }
    })

    return predictions
  }
