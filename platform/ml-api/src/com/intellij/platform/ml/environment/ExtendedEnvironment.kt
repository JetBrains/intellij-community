// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.environment

import com.intellij.platform.ml.*
import com.intellij.platform.ml.ScopeEnvironment.Companion.accessibleSafelyByOrNull
import com.intellij.platform.ml.environment.EnvironmentExtender.Companion.extendTierInstance
import org.jetbrains.annotations.ApiStatus

/**
 * An environment that is built in to fulfill a [TierRequester]'s requirements.
 *
 * When built, it accepts all available extenders, and it is trying to resolve their
 * order to extend particular tiers.
 *
 * If there are some main tiers passed to build the extended environment, they will
 * not be overridden.
 *
 * Among the available extenders, passed to the constructor, there could be some that
 * extend the same tier.
 * This could signify that an instance of the same tier can be mined from
 * different sets of objects (requirements).
 * But if there is more than one extender, that could potentially be run that will
 * extend a tier of the same instance, then [IllegalArgumentException] will be thrown
 * telling, that there is an ambiguity.
 *
 * When we have determined which extenders could potentially be run, we try to determine
 * the order with topological sort: [com.intellij.platform.ml.environment.TopologicalSortingResolver].
 * If there is a circle in the requirements, it will throw [com.intellij.platform.ml.environment.CircularRequirementException].
 *
 * @param environmentExtenders Extenders that will be used to build the extended environment.
 * @param mainEnvironment An already existing environment, instances from which shall not be overridden.
 * @param systemLoggerBuilder Logs information that is useful for the application's debug
 *
 * @return An environment that contains all tiers from the main environment plus
 * all tiers that it was possible to acquire via the passed extenders.
 */
@ApiStatus.Internal
class ExtendedEnvironment(environmentExtenders: List<EnvironmentExtender<*>>, mainEnvironment: Environment, systemLoggerBuilder: SystemLoggerBuilder) : Environment {
  private val storage: Environment

  init {
    val nonOverridingExtenders = environmentExtenders.filter { it.extendingTier !in mainEnvironment }
    storage = buildExtendedEnvironment(
      nonOverridingExtenders.map { it.extendingTier }.toSet() + mainEnvironment.tiers,
      nonOverridingExtenders + mainEnvironment.separateIntoExtenders(),
      mainEnvironment.tiers,
      systemLoggerBuilder.build(this::class.java)
    )
  }

  override val tiers: Set<Tier<*>>
    get() = storage.tiers

  override fun <T : Any> getInstance(tier: Tier<T>): T {
    return storage.getInstance(tier)
  }

  companion object {
    private val ENVIRONMENT_RESOLVER = TopologicalSortingResolver()

    /**
     * Creates an [Environment] that contents tiers from [tiers], that were successfully extended by [extenders]
     */
    private fun buildExtendedEnvironment(tiers: Set<Tier<*>>,
                                         extenders: List<EnvironmentExtender<*>>,
                                         mainTiers: Set<Tier<*>>,
                                         systemLogger: SystemLogger): Environment {
      val validatedExtendersPerTier = validateExtenders(tiers, extenders)
      val extensionOrder = ENVIRONMENT_RESOLVER.resolve(validatedExtendersPerTier)
      val storage = TierInstanceStorage()

      val extensionOutcome: List<ExtensionOutcome> = extensionOrder.map { environmentExtender ->
        val safelyAccessibleEnvironment = storage.accessibleSafelyByOrNull(environmentExtender)
                                          ?: return@map ExtensionOutcome.InsufficientEnvironment(environmentExtender)
        environmentExtender.extendTierInstance(safelyAccessibleEnvironment)?.let { extendedTierInstance ->
          storage.putTierInstance(extendedTierInstance)
          ExtensionOutcome.Success(environmentExtender, extendedTierInstance)
        } ?: ExtensionOutcome.NullReturned(environmentExtender)
      }

      systemLogger.debug {
        "Extending environment having ${mainTiers}\n" +
        extensionOutcome
          .filterNot { it.environmentExtender is ContainingExtender<*> }
          .withIndex()
          .joinToString("\n") { (index, outcome) -> "  extender #$index: $outcome" }
      }

      return storage
    }

    private sealed class ExtensionOutcome {
      abstract val environmentExtender: EnvironmentExtender<*>

      data class Success(override val environmentExtender: EnvironmentExtender<*>, val instance: Any) : ExtensionOutcome() {
        override fun toString() = "[success] ${environmentExtender.javaClass.simpleName} -> ${environmentExtender.extendingTier}"
      }

      data class InsufficientEnvironment(override val environmentExtender: EnvironmentExtender<*>) : ExtensionOutcome() {
        override fun toString() = "[insufficient environment] ${environmentExtender.javaClass.simpleName} "
      }

      data class NullReturned(override val environmentExtender: EnvironmentExtender<*>) : ExtensionOutcome() {
        override fun toString() = "[null returned] ${environmentExtender.javaClass.simpleName} "
      }
    }

    private fun validateExtenders(tiers: Set<Tier<*>>, extenders: List<EnvironmentExtender<*>>): Map<Tier<*>, EnvironmentExtender<*>> {
      val extendableTiers: Set<Tier<*>> = extenders.map { it.extendingTier }.toSet()

      val runnableExtenders = extenders
        .filter { desiredExtender ->
          desiredExtender.requiredTiers.all { requirementForDesiredExtender -> requirementForDesiredExtender in extendableTiers }
        }

      val ambiguouslyExtendableTiers: MutableList<Pair<Tier<*>, List<EnvironmentExtender<*>>>> = mutableListOf()
      val extendersPerTier: Map<Tier<*>, EnvironmentExtender<*>> = runnableExtenders
        .groupBy { it.extendingTier }
        .mapNotNull { (tier, tierExtenders) ->
          if (tierExtenders.size > 1) {
            ambiguouslyExtendableTiers.add(tier to tierExtenders)
            null
          }
          else
            tierExtenders.first()
        }
        .associateBy { it.extendingTier }
        .filterKeys { it in tiers }

      require(ambiguouslyExtendableTiers.isEmpty()) { "Some tiers could be extended ambiguously: $ambiguouslyExtendableTiers" }

      return extendersPerTier
    }
  }
}

internal class ContainingExtender<T : Any>(private val containingEnvironment: Environment, private val tier: Tier<T>) : EnvironmentExtender<T> {
  override val extendingTier: Tier<T> = tier

  override fun extend(environment: Environment): T {
    return containingEnvironment[tier]
  }

  override val requiredTiers: Set<Tier<*>> = emptySet()
}

private fun Environment.separateIntoExtenders(): List<EnvironmentExtender<*>> {
  return this.tiers.map { tier -> ContainingExtender(this, tier) }
}
