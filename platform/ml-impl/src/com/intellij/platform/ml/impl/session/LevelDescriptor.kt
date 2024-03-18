// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.platform.ml.*
import com.intellij.platform.ml.ScopeEnvironment.Companion.restrictedBy
import com.intellij.platform.ml.TierRequester.Companion.fulfilledBy
import com.intellij.platform.ml.impl.DescriptionComputer
import com.intellij.platform.ml.impl.FeatureSelector
import com.intellij.platform.ml.impl.FeatureSelector.Companion.or
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getDescriptorsOfTiers
import com.intellij.platform.ml.impl.environment.ExtendedEnvironment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class LevelDescriptor(
  val apiPlatform: MLApiPlatform,
  val descriptionComputer: DescriptionComputer,
  val usedFeaturesSelectors: PerTier<FeatureSelector>,
  val notUsedFeaturesSelectors: PerTier<FeatureSelector>,
) {
  fun describe(
    upperLevels: List<DescribedLevel>,
    nextLevelMainEnvironment: Environment,
    nextLevelAdditionalTiers: Set<Tier<*>>
  ): DescribedLevel {
    val mainEnvironment = Environment.joined(listOf(
      Environment.of(upperLevels.flatMap { it.main.keys }),
      nextLevelMainEnvironment
    ))

    val availableEnvironment = ExtendedEnvironment(apiPlatform.environmentExtenders, mainEnvironment)
    val extendedEnvironment = availableEnvironment.restrictedBy(nextLevelMainEnvironment.tiers + nextLevelAdditionalTiers)

    val runnableDescriptorsPerTier = apiPlatform.getDescriptorsOfTiers(extendedEnvironment.tiers)
      .mapValues { (_, descriptors) -> descriptors.fulfilledBy(availableEnvironment) }

    val extendedEnvironmentDescription = runnableDescriptorsPerTier
      .mapValues { (tier, tierDescriptors) ->
        describeTier(tier, tierDescriptors, availableEnvironment)
      }

    val nextLevel = DescribedLevel(
      main = nextLevelMainEnvironment.tierInstances.associateWith { mainTierInstance ->
        DescribedTierData(extendedEnvironmentDescription.getValue(mainTierInstance.tier))
      },
      additional = extendedEnvironment.restrictedBy(nextLevelAdditionalTiers).tierInstances.associateWith { mainTierInstance ->
        DescribedTierData(extendedEnvironmentDescription.getValue(mainTierInstance.tier))
      },
    )

    return nextLevel
  }

  private fun createFilterOfFeaturesToCompute(tier: Tier<*>, tierDescriptors: List<TierDescriptor>): FeatureFilter {
    if (tierDescriptors.any { it is ObsoleteTierDescriptor }) {
      return FeatureFilter.ACCEPT_ALL
    }

    val tierFeaturesSelector = (usedFeaturesSelectors[tier] ?: FeatureSelector.NOTHING) or notUsedFeaturesSelectors.getValue(tier)
    val tierComputableFeatures = tierDescriptors.flatMap { it.descriptionDeclaration }.toSet()
    val tierToComputeSelection = tierFeaturesSelector.select(tierComputableFeatures)

    if (tierToComputeSelection is FeatureSelector.Selection.Incomplete) {
      throw IncompleteDescriptionException(tier, tierToComputeSelection.selectedFeatures, tierToComputeSelection.details)
    }

    return FeatureFilter { it in tierToComputeSelection.selectedFeatures }
  }

  // Filter assumes that the feature is either used or not used by the model
  private fun createFilterOfUsedFeatures(tier: Tier<*>): FeatureFilter {
    val usedFeaturesSelector = usedFeaturesSelectors[tier] ?: FeatureSelector.NOTHING
    val notUsedFeaturesSelector = notUsedFeaturesSelectors.getValue(tier)
    return FeatureFilter {
      val featureIsUsed = usedFeaturesSelector.select(it)
      val featureIsNotUsed = notUsedFeaturesSelector.select(it)
      assert(featureIsUsed || featureIsNotUsed) {
        "Feature $it of $tier must not have been computed. It is not used by the ML model or marked as not used"
      }
      require(featureIsUsed xor featureIsNotUsed) {
        "${it} of $tier is used by the ML model, but marked as not used at the same time"
      }
      featureIsUsed
    }
  }

  private fun Set<Feature>.splitByUsage(usableFeaturesFilter: FeatureFilter): Usage<Set<Feature>> {
    val usedFeatures = this.filter { usableFeaturesFilter.accept(it.declaration) }
    val notUsedFeatures = this.filter { !usableFeaturesFilter.accept(it.declaration) }
    return Usage(usedFeatures.toSet(), notUsedFeatures.toSet())
  }

  private fun makeDescriptionPartition(descriptor: TierDescriptor,
                                       computedDescription: Set<Feature>,
                                       usedFeaturesFilter: FeatureFilter): DescriptionPartition {

    val computedDescriptionDeclaration = computedDescription.map { it.declaration }.toSet()

    if (descriptor is ObsoleteTierDescriptor) {
      val nonDeclaredDescription = computedDescription.filter { it.declaration !in descriptor.partialDescriptionDeclaration }.toSet()
      apiPlatform.manageNonDeclaredFeatures(descriptor, nonDeclaredDescription)
    }
    else {
      val notDeclaredFeaturesDeclarations = computedDescriptionDeclaration - descriptor.descriptionDeclaration
      require(notDeclaredFeaturesDeclarations.isEmpty()) {
        """
          $descriptor described environment with some features that were not declared:
          ${notDeclaredFeaturesDeclarations.map { it.name }}
          computed declaration: ${computedDescriptionDeclaration}
          declared declaration: ${descriptor.descriptionDeclaration}
        """.trimIndent()
      }
    }

    val maybePartialDescriptionDeclaration = if (descriptor is ObsoleteTierDescriptor)
      descriptor.partialDescriptionDeclaration
    else
      descriptor.descriptionDeclaration

    val notComputedDescriptionDeclaration = maybePartialDescriptionDeclaration - computedDescriptionDeclaration
    for (notComputedFeatureDeclaration in notComputedDescriptionDeclaration) {
      require(!usedFeaturesFilter.accept(notComputedFeatureDeclaration)) {
        "Feature ${notComputedFeatureDeclaration} was expected to be computed by $descriptor, " +
        "because was declared and accepted by the feature filter. Computed declaration: $computedDescriptionDeclaration"
      }
    }

    val declaredFeatures = mutableSetOf<Feature>()
    val nonDeclaredFeatures = mutableSetOf<Feature>()

    if (descriptor is ObsoleteTierDescriptor) {
      computedDescription.forEach {
        if (it.declaration in descriptor.partialDescriptionDeclaration)
          declaredFeatures += it
        else
          nonDeclaredFeatures += it
      }
    }
    else {
      declaredFeatures.addAll(computedDescription)
    }

    return Declaredness(declaredFeatures.splitByUsage(usedFeaturesFilter), nonDeclaredFeatures.splitByUsage(usedFeaturesFilter))
  }

  private fun describeTier(tier: Tier<*>, tierDescriptors: List<TierDescriptor>, environment: Environment): DescriptionPartition {
    val toComputeFilter = createFilterOfFeaturesToCompute(tier, tierDescriptors)
    val usefulTierDescriptors = tierDescriptors.filter { it.couldBeUseful(toComputeFilter) }

    val description: Map<TierDescriptor, Set<Feature>> = descriptionComputer.computeDescription(
      tier,
      usefulTierDescriptors,
      environment,
      toComputeFilter
    )

    val usedByModelFilter = createFilterOfUsedFeatures(tier)

    val descriptionPartition = description.entries
      .map { (tierDescriptor, computedDescription) ->
        makeDescriptionPartition(tierDescriptor, computedDescription, usedByModelFilter)
      }
      .reduceOrNull { first, second ->
        Declaredness(
          declared = Usage(used = first.declared.used + second.declared.used,
                           notUsed = first.declared.notUsed + second.declared.notUsed),
          nonDeclared = Usage(used = first.nonDeclared.used + second.nonDeclared.used,
                              notUsed = first.nonDeclared.notUsed + second.nonDeclared.notUsed)
        )
      } ?: Declaredness(Usage(emptySet(), emptySet()), Usage(emptySet(), emptySet()))

    return descriptionPartition
  }
}

@ApiStatus.Internal
class IncompleteDescriptionException(tier: Tier<*>,
                                     selectedFeatures: Set<FeatureDeclaration<*>>,
                                     missingFeaturesDetails: String) : Exception() {
  override val message = "Computable description of tier $tier is not sufficient: $missingFeaturesDetails. Computed features: $selectedFeatures"
}
