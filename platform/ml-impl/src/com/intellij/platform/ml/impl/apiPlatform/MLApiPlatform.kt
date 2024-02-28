// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.apiPlatform

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.MLTask
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproachInitializer
import com.intellij.platform.ml.impl.logger.MLEvent
import com.intellij.platform.ml.impl.logger.MLEventsLogger
import com.intellij.platform.ml.impl.monitoring.*
import com.intellij.platform.ml.impl.monitoring.MLApproachListener.Companion.asJoinedListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.Companion.onAttemptedToStartSession
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.Companion.targetedApproaches
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an environment, that provides extendable parts of the ML API.
 *
 * Each entity inside the API could access the platform, it is running within,
 * as everything happens after [com.intellij.platform.ml.impl.MLTaskApproachInitializer.initializeApproachWithin],
 * where the platform is acknowledged.
 *
 * All usages of the ij platform functionality (extension points, registry keys, etc.) shall be
 * accessed via this class.
 */
@ApiStatus.Internal
abstract class MLApiPlatform {
  private val finishedInitialization = lazy { MLApiPlatformInitializationProcess() }
  private var initializationStage: InitializationStage = InitializationStage.NotStarted

  /**
   * Each [MLTaskApproach] is initialized only once during the application's lifetime.
   * This function keeps track of all approaches that were initialized already, and initializes
   * them when they are first needed.
   */
  fun <P : Any> accessApproachFor(task: MLTask<P>): MLTaskApproach<P> {
    return finishedInitialization.value.getApproachFor(task)
  }


  /**
   * The extendable static state of the platform that must be fixed to create FUS group's validator.
   *
   * These values must be static, as they define the FUS scheme
   */
  val staticState: StaticState
    get() = StaticState(tierDescriptors, environmentExtenders, taskApproaches)

  /**
   * The descriptors that are available in the platform.
   * This value is interchangeable during the application runtime,
   * see [staticState].
   */
  abstract val tierDescriptors: List<TierDescriptor>

  /**
   * The complete list of environment extenders, available in the platform.
   * This value is interchangeable during the application runtime,
   * see [staticState].
   */
  abstract val environmentExtenders: List<EnvironmentExtender<*>>

  /**
   * The complete list of the approaches for ML tasks, available in the platform.
   * This value is interchangeable during the application runtime,
   * see [staticState].
   */
  abstract val taskApproaches: List<MLTaskApproachInitializer<*>>


  /**
   * All the objects, that are listening execution of ML tasks.
   * The collection is mutable, so new listeners could be added via [addTaskListener].
   *
   * This value is mutable, new listeners could be added anytime.
   */
  abstract val taskListeners: List<MLTaskGroupListener>

  /**
   * Adds another provider for ML tasks' execution process monitoring dynamically.
   * The event could be removed via the corresponding [ExtensionController.removeExtension] call.
   * See [taskListeners].
   */
  abstract fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController

  /**
   * ML events that will be written to FUS logs.
   * As FUS is initialized only once, on the application's startup, they all must be registered
   * before that via [addMLEventBeforeFusInitialized].
   *
   * This value could be mutable, however, only during a short period of time: after the application's startup,
   * and before FUS logs initialization.
   */
  abstract val events: List<MLEvent>

  /**
   * Adds another ML event dynamically.
   * The event could be removed via the corresponding [ExtensionController.removeExtension] call.
   */
  fun addMLEventBeforeFusInitialized(event: MLEvent): ExtensionController {
    when (val stage = initializationStage) {
      is InitializationStage.Failed -> throw Exception("Initialization of ML Api Platform has failed, events could not be added.", stage.asException)
      is InitializationStage.Cancelled -> throw Exception("Initialization of ML Api Platform has been cancelled")
      is InitializationStage.PotentiallySuccessful -> {
        require(stage.order <= InitializationStage.InitializingApproaches.order) {
          "FUS group initialization has already been started, not allowed to register more ML Events"
        }
        return addEvent(event)
      }
    }
  }

  /**
   * The complete list of the listeners that are listening to the process of an MLApiPlatform's initialization.
   * The initialization is performed in [finishedInitialization]'s init block.
   *
   * This value could be mutable, so
   * additional listeners could be added via [addStartupListener].
   *
   * If a listener was added after a certain initialization stage,
   * only callbacks of those stages will be triggered later that have not happened yet.
   */
  abstract val startupListeners: List<MLApiStartupListener>

  /**
   * Adds another startup listener.
   * The listener could be removed via the corresponding [ExtensionController.removeExtension] call.
   */
  abstract fun addStartupListener(listener: MLApiStartupListener): ExtensionController


  /**
   * Declares how the computed but non-declared features will be handled.
   */
  abstract fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>)


  internal abstract fun addEvent(event: MLEvent): ExtensionController

  fun interface ExtensionController {
    fun removeExtension()
  }

  data class StaticState(
    val tierDescriptors: List<TierDescriptor>,
    val environmentExtenders: List<EnvironmentExtender<*>>,
    val taskApproaches: List<MLTaskApproachInitializer<*>>,
  )

  private sealed class InitializationStage(
    val callListener: (MLApiStartupProcessListener) -> Unit,
    val allowStartup: Boolean
  ) {
    sealed class PotentiallySuccessful(val order: Int, callListener: (MLApiStartupProcessListener) -> Unit, allowStartup: Boolean) : InitializationStage(callListener, allowStartup)

    data object Cancelled : InitializationStage({ it.onCanceled() }, allowStartup = true)

    class Failed(lastStage: InitializationStage, nextStage: InitializationStage, exception: Throwable, callListener: (MLApiStartupProcessListener) -> Unit) : InitializationStage(callListener, allowStartup = false) {
      val asException = Exception("Failed to proceed from the initialization stage $lastStage to $nextStage", exception)
    }

    data object NotStarted : PotentiallySuccessful(0, {}, allowStartup = true)
    data object InitializingApproaches : PotentiallySuccessful(1, { it.onStartedInitializingApproaches() }, allowStartup = true)
    data class InitializingFUS(val initializedApproaches: Collection<InitializerAndApproach<*>>) : PotentiallySuccessful(2, {
      it.onStartedInitializingFus(initializedApproaches)
    }, allowStartup = false)

    data object Finished : PotentiallySuccessful(3, { it.onFinished() }, allowStartup = false)
  }

  private inner class MLApiPlatformInitializationProcess {
    val approachPerTask: Map<MLTask<*>, MLTaskApproach<*>>
    private val completeInitializersList: List<MLTaskApproachInitializer<*>> = taskApproaches.toMutableList()

    init {
      require(initializationStage.allowStartup) {
        """
        ML API Platform's initialization should not be run twice. Current state is $initializationStage
        """.trimIndent()
      }

      fun currentStartupListeners(): List<MLApiStartupProcessListener> = startupListeners.map { it.onBeforeStarted(this@MLApiPlatform) }

      fun <T> proceedToNextStage(nextStage: InitializationStage.PotentiallySuccessful, action: () -> T): T {
        return try {
          action().also {
            currentStartupListeners().forEach { nextStage.callListener(it) }
            initializationStage = nextStage
          }
        }
        catch (ex: ProcessCanceledException) {
          initializationStage = InitializationStage.Cancelled
          throw ex
        }
        catch (ex: Throwable) {
          val failure = InitializationStage.Failed(initializationStage, nextStage, ex) { it.onFailed(ex) }
          initializationStage = failure
          throw failure.asException
        }
      }

      proceedToNextStage(InitializationStage.InitializingApproaches) {}

      val initializedApproachPerTask = mutableListOf<InitializerAndApproach<*>>()

      approachPerTask = proceedToNextStage(InitializationStage.InitializingFUS(initializedApproachPerTask)) {
        completeInitializersList.validate()

        fun <T : Any> initializeApproach(approachInitializer: MLTaskApproachInitializer<T>) {
          initializedApproachPerTask.add(InitializerAndApproach(
            approachInitializer,
            approachInitializer.initializeApproachWithin(this@MLApiPlatform)
          ))
        }
        completeInitializersList.forEach { initializeApproach(it) }
        initializedApproachPerTask.associate { it.initializer.task to it.approach }
      }

      proceedToNextStage(InitializationStage.Finished) {
        MLEventsLogger.Manager.ensureInitialized(okIfInitializing = true, this@MLApiPlatform)
      }
    }

    fun <P : Any> getApproachFor(task: MLTask<P>): MLTaskApproach<P> {
      val taskApproach = requireNotNull(approachPerTask[task]) {
        val mainMessage = "No approach for task $task was found"
        val lateRegistrationMessage = getLateApproachRegistrationAssumption(task)
        if (lateRegistrationMessage != null) "$mainMessage. $lateRegistrationMessage" else mainMessage
      }

      @Suppress("UNCHECKED_CAST")
      return taskApproach as MLTaskApproach<P>
    }

    private fun getLateApproachRegistrationAssumption(task: MLTask<*>): String? {
      val currentInitializersList = taskApproaches.toMutableList()
      if (completeInitializersList == currentInitializersList) return null
      val taskApproaches = currentInitializersList.filter { it.task == task }
      if (taskApproaches.isEmpty()) return null
      require(taskApproaches.size == 1) { "More than one approach for task $task: $taskApproaches" }
      return "Approach ${taskApproaches.first()} for task ${task.name} was registered after the ML API Platform was initialized"
    }

    private fun List<MLTaskApproachInitializer<*>>.validate() {
      val duplicateInitializerPerTask = this.groupBy { it.task }.filter { it.value.size > 1 }
      require(duplicateInitializerPerTask.isEmpty()) {
        "Found more than one approach for the following tasks: ${duplicateInitializerPerTask}"
      }
    }
  }

  companion object {
    fun MLApiPlatform.getDescriptorsOfTiers(tiers: Set<Tier<*>>): PerTier<List<TierDescriptor>> {
      val descriptorsPerTier = tierDescriptors.groupBy { it.tier }
      return tiers.associateWith { descriptorsPerTier[it] ?: emptyList() }
    }

    fun MLApiPlatform.ensureApproachesInitialized() {
      when (val stage = initializationStage) {
        is InitializationStage.Failed -> throw Exception("Unable to ensure that approaches are initialized", stage.asException)
        InitializationStage.NotStarted -> finishedInitialization.value
        InitializationStage.Cancelled -> finishedInitialization.value
        InitializationStage.InitializingApproaches -> throw Exception("Recursion detected while initializing approaches")
        is InitializationStage.InitializingFUS -> return
        InitializationStage.Finished -> return
      }
    }

    fun <R, P : Any> MLApiPlatform.getJoinedListenerForTask(taskApproach: MLTaskApproach<P>,
                                                            permanentSessionEnvironment: Environment): MLApproachListener<R, P> {
      val relevantGroupListeners = taskListeners.filter { taskApproach.javaClass in it.targetedApproaches }
      val approachListeners = relevantGroupListeners.mapNotNull {
        it.onAttemptedToStartSession<P, R>(taskApproach, permanentSessionEnvironment)
      }
      return approachListeners.asJoinedListener()
    }
  }
}
