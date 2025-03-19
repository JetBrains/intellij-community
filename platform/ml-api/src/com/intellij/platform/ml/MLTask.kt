// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.ScopeEnvironment.Companion.restrictedBy
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.session.DescribedTierScheme
import org.jetbrains.annotations.ApiStatus


/**
 * Is a declaration of a place in code, where a classical machine learning approach
 * is desired to be applied.
 *
 * The proper way to create new tasks is to create a static object-inheritor of this class
 * (see inheritors of this class to find all implemented tasks in your project).
 *
 * The first level in [levels] is usually called "permanentSessionEnvironment"
 *
 * @param name The unique name of an ML Task
 * @param levels The main tiers of the task that will be provided within the task's application place
 * @param callParameters Parameters that were available in the place, where the task is applied.
 * Should be used to pass additional objects to
 *  - Model's acquisition [com.intellij.platform.ml.MLModel.Provider.provideModel]
 *  - The prediction [com.intellij.platform.ml.MLModel.predict]
 *  - During the analysis, the call parameters are saved in the session tree, see [com.intellij.platform.ml.session.LevelData].
 * @param predictionClass The class of an object, that will serve as "prediction"
 * @param P The type of prediction
 */
@ApiStatus.Internal
abstract class MLTask<P : Any> protected constructor(
  val name: String,
  val levels: List<Set<Tier<*>>>,
  val callParameters: List<Set<Tier<*>>>,
  val predictionClass: Class<P>
) {
  init {
    require(levels.isNotEmpty()) {
      """
        Task $this should contain at least one level
      """.trimIndent()
    }
    require(callParameters.size == levels.size) {
      """
        Task $this has ${levels.size} levels, but in call parameters there are ${callParameters.size} levels.
        Each level must have its own set of call parameters.
      """.trimIndent()
    }
  }
}

/**
 * A method of approaching an ML task.
 * Usually, it is inferencing an ML model and collecting logs.
 *
 * Each [MLTaskApproach] is initialized once by the corresponding [MLTaskApproachBuilder],
 * then the [apiPlatform] is fixed.
 *
 * @see [com.intellij.platform.ml.LogDrivenModelInference] for currently used approach.
 */
@ApiStatus.Internal
interface MLTaskApproach<P : Any> {
  /**
   * The task this approach is solving.
   * Each approach is dedicated to one and only task, and it is aware of it.
   */
  val task: MLTask<P>

  /**
   * The platform, this approach is called within, that was provided by [MLTaskApproachBuilder]
   */
  val apiPlatform: MLApiPlatform

  /**
   * Acquire the ML model and start the session.
   *
   * @param callParameters The parameters that were available in the place where the task is applied.
   * The set of tiers must be equal to the first level of [MLTask.callParameters].
   * @param permanentSessionEnvironment The instances of the first level of [MLTask.levels], that will be described
   * to call the ML model.
   *
   * @return [Session.StartOutcome.Failure] if something went wrong during the start, [Session.StartOutcome.Success]
   * which contains the started session otherwise.
   */
  suspend fun startSession(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P>

  companion object {
    fun <P : Any> findMlTaskApproach(task: MLTask<P>, apiPlatform: MLApiPlatform): MLTaskApproachBuilder<P> {
      val taskApproachBuilder = requireNotNull(apiPlatform.taskApproaches.find { it.task == task }) {
        """
        No approach for task $task was registered in $apiPlatform.
        Available approaches: ${apiPlatform.taskApproaches}
        """.trimIndent()
      }
      @Suppress("UNCHECKED_CAST")
      return taskApproachBuilder as MLTaskApproachBuilder<P>
    }

    suspend fun <P : Any> startMLSession(task: MLTask<P>,
                                         apiPlatform: MLApiPlatform,
                                         callParameters: Environment,
                                         permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
      validateEnvironment(callParameters, task.callParameters.first(), permanentSessionEnvironment, task.levels.first())
      val approach = findMlTaskApproach(task, apiPlatform).buildApproach(apiPlatform)
      val safelyAccessibleCallParameters = callParameters.restrictedBy(task.callParameters.first())
      return approach.startSession(safelyAccessibleCallParameters, permanentSessionEnvironment)
    }

    suspend fun <P : Any> MLTask<P>.startMLSession(callParameters: Environment, permanentSessionEnvironment: Environment, apiPlatform: MLApiPlatform): Session.StartOutcome<P> {
      return startMLSession(this@startMLSession, apiPlatform, callParameters, permanentSessionEnvironment)
    }

    private fun validateEnvironment(callParameters: Environment,
                                    expectedCallParameters: Set<Tier<*>>,
                                    permanentSessionEnvironment: Environment,
                                    expectedPermanentTiers: Set<Tier<*>>) {
      require(callParameters.tiers == expectedCallParameters) {
        """
        Invalid call parameters passed.
          Missing: ${expectedCallParameters - callParameters.tiers}
          Redundant: ${callParameters.tiers - expectedCallParameters}
        """.trimIndent()
      }
      require(permanentSessionEnvironment.tiers == expectedPermanentTiers) {
        """
        Invalid main environment passed.
          Missing: ${expectedPermanentTiers - permanentSessionEnvironment.tiers}
          Redundant: ${permanentSessionEnvironment.tiers - expectedPermanentTiers}
        """.trimIndent()
      }
    }
  }
}

/**
 * Initializes an [MLTaskApproach]
 */
@ApiStatus.Internal
interface MLTaskApproachBuilder<P : Any> {
  /**
   * The task, that the created [MLTaskApproach] is dedicated to solve.
   */
  val task: MLTask<P>

  /**
   * Initializes the approach.
   * It is called each time, when another ml session is started.
   *
   * @param apiPlatform The platform, that is used to initialize approach's components.
   * All [TierDescriptor]s, [com.intellij.platform.ml.environment.EnvironmentExtender]s should be already final when this method is called.
   */
  fun buildApproach(apiPlatform: MLApiPlatform): MLTaskApproach<P>

  /**
   * Builds a scheme for the finished event.
   */
  fun buildApproachSessionDeclaration(apiPlatform: MLApiPlatform): List<DescribedLevelScheme>
}

@ApiStatus.Internal
class LevelSignature<M, A>(
  val main: M,
  val additional: A
)

typealias DescribedLevelScheme = LevelSignature<PerTier<DescribedTierScheme>, PerTier<DescribedTierScheme>>

typealias LevelTiers = LevelSignature<Set<Tier<*>>, Set<Tier<*>>>
