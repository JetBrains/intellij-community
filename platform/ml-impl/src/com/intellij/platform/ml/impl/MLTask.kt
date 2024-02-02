// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.session.AdditionalTierScheme
import com.intellij.platform.ml.impl.session.Level
import com.intellij.platform.ml.impl.session.MainTierScheme
import org.jetbrains.annotations.ApiStatus


/**
 * Is a declaration of a place in code, where a classical machine learning approach
 * is desired to be applied.
 *
 * The proper way to create new tasks is to create a static object-inheritor of this class
 * (see inheritors of this class to find all implemented tasks in your project).
 *
 * @param name The unique name of an ML Task
 * @param levels The main tiers of the task that will be provided within the task's application place
 * @param predictionClass The class of an object, that will serve as "prediction"
 * @param T The type of prediction
 */
@ApiStatus.Internal
abstract class MLTask<T : Any> protected constructor(
  val name: String,
  val levels: List<Set<Tier<*>>>,
  val predictionClass: Class<T>
)

/**
 * A method of approaching an ML task.
 * Usually, it is inferencing an ML model and collecting logs.
 *
 * Each [MLTaskApproach] is initialized once by the corresponding [MLTaskApproachInitializer],
 * then the [apiPlatform] is fixed.
 *
 * @see [com.intellij.platform.ml.impl.approach.LogDrivenModelInference] for currently used approach.
 */
@ApiStatus.Internal
interface MLTaskApproach<P : Any> {
  /**
   * The task this approach is solving.
   * Each approach is dedicated to one and only task, and it is aware of it.
   */
  val task: MLTask<P>

  /**
   * The platform, this approach is called within, that was provided by [MLTaskApproachInitializer]
   */
  val apiPlatform: MLApiPlatform

  /**
   * A static declaration of the features, used in the approach.
   */
  val approachDeclaration: Declaration

  /**
   * Acquire the ML model and start the session.
   *
   * @return [Session.StartOutcome.Failure] if something went wrong during the start, [Session.StartOutcome.Success]
   * which contains the started session otherwise.
   */
  fun startSession(permanentSessionEnvironment: Environment): Session.StartOutcome<P>

  data class Declaration(
    val sessionFeatures: Map<String, Set<FeatureDeclaration<*>>>,
    val levelsScheme: List<LevelScheme>
  )

  companion object {
    fun <P : Any> findMlApproach(task: MLTask<P>, apiPlatform: MLApiPlatform = ReplaceableIJPlatform): MLTaskApproach<P> {
      return apiPlatform.accessApproachFor(task)
    }

    fun <P : Any> startMLSession(task: MLTask<P>,
                                 permanentSessionEnvironment: Environment,
                                 apiPlatform: MLApiPlatform = ReplaceableIJPlatform): Session.StartOutcome<P> {
      val approach = findMlApproach(task, apiPlatform)
      return approach.startSession(permanentSessionEnvironment)
    }

    fun <P : Any> MLTask<P>.startMLSession(permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
      return startMLSession(this, permanentSessionEnvironment)
    }

    fun <P : Any> MLTask<P>.startMLSession(permanentTierInstances: Iterable<TierInstance<*>>): Session.StartOutcome<P> {
      return this.startMLSession(Environment.of(permanentTierInstances))
    }

    fun <P : Any> MLTask<P>.startMLSession(vararg permanentTierInstances: TierInstance<*>): Session.StartOutcome<P> {
      return this.startMLSession(Environment.of(*permanentTierInstances))
    }
  }
}

/**
 * Initializes an [MLTaskApproach]
 */
@ApiStatus.Internal
interface MLTaskApproachInitializer<P : Any> {
  /**
   * The task, that the created [MLTaskApproach] is dedicated to solve.
   */
  val task: MLTask<P>

  /**
   * Initializes the approach.
   * It is called only once during the application's runtime.
   * So it is crucial that this function will accept the [MLApiPlatform] you want it to.
   *
   * To access the API in order to build event validator statically,
   * FUS uses the actual [com.intellij.platform.ml.impl.apiPlatform.IJPlatform], which could be problematic if you
   * want to test FUS logs.
   * So make sure that you will replace it with your test platform in
   * time via [com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform.replacingWith].
   */
  fun initializeApproachWithin(apiPlatform: MLApiPlatform): MLTaskApproach<P>

  companion object {
    val EP_NAME = ExtensionPointName<MLTaskApproachInitializer<*>>("com.intellij.platform.ml.impl.approach")
  }
}

typealias LevelScheme = Level<PerTier<MainTierScheme>, PerTier<AdditionalTierScheme>>

typealias LevelTiers = Level<Set<Tier<*>>, Set<Tier<*>>>
