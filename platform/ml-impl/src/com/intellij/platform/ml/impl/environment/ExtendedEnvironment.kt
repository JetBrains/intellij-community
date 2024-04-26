// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.environment

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ml.*
import com.intellij.platform.ml.EnvironmentExtender.Companion.extendTierInstance
import com.intellij.platform.ml.ScopeEnvironment.Companion.accessibleSafelyByOrNull
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
 * the order with topological sort: [com.intellij.platform.ml.impl.environment.TopologicalSortingResolver].
 * If there is a circle in the requirements, it will throw [com.intellij.platform.ml.impl.environment.CircularRequirementException].
 */
@ApiStatus.Internal
class ExtendedEnvironment : Environment {
  private val storage: Environment

  /**
   * @param environmentExtenders Extenders, that will be used to extend the [tiersToExtend].
   * @param mainEnvironment Tiers that are already determined and shall not be replaced.
   * @param tiersToExtend Tiers that should be put to the extended environment via [environmentExtenders].
   * It could not be guaranteed that all desired tiers will be extended.
   *
   * @return An environment that contains all tiers from [mainEnvironment] plus
   * some tiers from [tiersToExtend], if it was possible to extend them.
   */
  constructor(environmentExtenders: List<EnvironmentExtender<*>>,
              mainEnvironment: Environment,
              tiersToExtend: Set<Tier<*>>) {
    val alreadyExistingButRequestedTiers = tiersToExtend.filter { it in mainEnvironment }
    require(alreadyExistingButRequestedTiers.isEmpty()) {
      """
      Requested to extend $alreadyExistingButRequestedTiers, but they already exist in the main environment
      (which contains ${mainEnvironment.tiers})
    """.trimIndent()
    }
    val nonOverridingExtenders = environmentExtenders.filter { it.extendingTier !in mainEnvironment }
    storage = buildExtendedEnvironment(
      tiersToExtend + mainEnvironment.tiers,
      nonOverridingExtenders + mainEnvironment.separateIntoExtenders(),
      mainEnvironment.tiers
    )
  }

  /**
   * @param environmentExtenders Extenders that will be utilized to build the extended environment.
   * @param mainEnvironment An already existing environment, instances from which shall not be overridden.
   *
   * @return An environment that contains all tiers from [mainEnvironment] plus
   * all tiers that it was possible to acquire via [environmentExtenders].
   */
  constructor(environmentExtenders: List<EnvironmentExtender<*>>,
              mainEnvironment: Environment) {
    val nonOverridingExtenders = environmentExtenders.filter { it.extendingTier !in mainEnvironment }
    storage = buildExtendedEnvironment(
      nonOverridingExtenders.map { it.extendingTier }.toSet() + mainEnvironment.tiers,
      nonOverridingExtenders + mainEnvironment.separateIntoExtenders(),
      mainEnvironment.tiers
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
                                         mainTiers: Set<Tier<*>>): Environment {
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

      logger<ExtendedEnvironment>().debug {
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
