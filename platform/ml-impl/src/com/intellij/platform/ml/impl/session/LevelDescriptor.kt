// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ml.*
import com.intellij.platform.ml.Feature.Companion.toCompactString
import com.intellij.platform.ml.ScopeEnvironment.Companion.restrictedBy
import com.intellij.platform.ml.TierRequester.Companion.fulfilledBy
import com.intellij.platform.ml.impl.DescriptionComputer
import com.intellij.platform.ml.impl.FeatureSelector
import com.intellij.platform.ml.impl.FeatureSelector.Companion.or
import com.intellij.platform.ml.impl.MLTask
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getDescriptorsOfTiers
import com.intellij.platform.ml.impl.environment.ExtendedEnvironment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class LevelDescriptor(
  val apiPlatform: MLApiPlatform,
  val descriptionComputer: DescriptionComputer,
  val usedFeaturesSelectors: PerTier<FeatureSelector>,
  val notUsedFeaturesFilters: PerTier<FeatureFilter>,
  val mlTask: MLTask<*>
) {
  suspend fun describe(
    nextLevelCallParameters: Environment,
    nextLevelMainEnvironment: Environment,
    upperLevels: List<DescribedLevel>,
    nextLevelAdditionalTiers: Set<Tier<*>>
  ): DescribedLevel {
    thisLogger().debug {
      """
      [${mlTask.name}] Describing level environment:
        Call parameters: ${nextLevelCallParameters.tiers}
        Main environment: ${nextLevelMainEnvironment.tiers}
        Additional environment: $nextLevelAdditionalTiers
    """.trimIndent()
    }

    val mainEnvironment = Environment.joined(listOf(
      Environment.of(upperLevels.flatMap { it.mainInstances.keys + it.additionalInstances.keys }),
      nextLevelMainEnvironment
    ))

    val availableEnvironment = ExtendedEnvironment(apiPlatform.environmentExtenders, mainEnvironment)
    val extendedEnvironment = availableEnvironment.restrictedBy(nextLevelMainEnvironment.tiers + nextLevelAdditionalTiers)

    thisLogger().debug {
      """
        [${mlTask.name}] Built the environment for description
          Available environment: ${availableEnvironment.tiers}
          Tier that will be described: ${extendedEnvironment.tiers}
      """.trimIndent()
    }

    val runnableDescriptorsPerTier = apiPlatform.getDescriptorsOfTiers(extendedEnvironment.tiers)
      .mapValues { (_, descriptors) -> descriptors.fulfilledBy(availableEnvironment) }

    val extendedEnvironmentDescription: Map<Tier<*>, Declaredness<Usage<Set<Feature>>>> = runnableDescriptorsPerTier
      .mapValues { (tier, tierDescriptors) ->
        describeTier(tier, tierDescriptors, availableEnvironment)
      }

    thisLogger().debug {
      "[${mlTask.name}] Described level environment:\n" + extendedEnvironmentDescription.map { (tier, description) ->
        "\t - $tier: $description"
      }.joinToString("\n")
    }

    val nextLevel = DescribedLevel(
      mainInstances = nextLevelMainEnvironment.tierInstances.associateWith { mainTierInstance ->
        DescribedTierData(extendedEnvironmentDescription.getValue(mainTierInstance.tier))
      },
      additionalInstances = extendedEnvironment.restrictedBy(nextLevelAdditionalTiers).tierInstances.associateWith { mainTierInstance ->
        DescribedTierData(extendedEnvironmentDescription.getValue(mainTierInstance.tier))
      },
      callParameters = nextLevelCallParameters
    )

    return nextLevel
  }

  private fun createFilterOfFeaturesToCompute(tier: Tier<*>, tierDescriptors: List<TierDescriptor>): FeatureFilter {
    if (tierDescriptors.any { it is ObsoleteTierDescriptor }) {
      return FeatureFilter.ACCEPT_ALL
    }

    val tierFeaturesSelector = (usedFeaturesSelectors[tier] ?: FeatureSelector.NOTHING) or notUsedFeaturesFilters.getValue(tier)
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
    return FeatureFilter {
      usedFeaturesSelector.select(it)
    }
  }

  private fun removeRedundantDescription(descriptor: TierDescriptor, computedDescriptionWithRedundancies: Set<Feature>): Set<Feature> {
    val usedFeaturesSelector = usedFeaturesSelectors[descriptor.tier] ?: FeatureSelector.NOTHING
    val notUsedFeaturesSelector = notUsedFeaturesFilters.getValue(descriptor.tier)

    return computedDescriptionWithRedundancies.mapNotNull { computedFeature ->
      val featureIsUsed = usedFeaturesSelector.select(computedFeature.declaration)
      val featureIsNotUsed = notUsedFeaturesSelector.accept(computedFeature.declaration)
      if (!featureIsUsed && !featureIsNotUsed) {
        if (!descriptor.descriptionPolicy.tolerateRedundantDescription)
          throw IllegalArgumentException(
            """
              Feature $computedFeature of $descriptor must not have been computed. It is not used by the ML model or marked as not used.
    
              You could set DescriptionPolicy.tolerateRedundantDescription to true, if this descriptor computes lightweight features,
              and redundantly computed features could be tolerated.
    
            """.trimIndent()
          )
        else return@mapNotNull null
      }
      require(!featureIsUsed || !featureIsNotUsed) {
        "${computedFeature.declaration.name} of $descriptor is used by the ML model, but marked as not used at the same time"
      }
      computedFeature
    }.toSet()
  }

  private fun validateDescription(descriptor: TierDescriptor, computedDescription: Set<Feature>, usedFeaturesFilter: FeatureFilter) {
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
    var expectedButNotComputedDescriptionDeclaration = notComputedDescriptionDeclaration
      .filter { usedFeaturesFilter.accept(it) }

    if (descriptor.descriptionPolicy.putNullImplicitly) {
      expectedButNotComputedDescriptionDeclaration = expectedButNotComputedDescriptionDeclaration
        .filterNot { it.type is FeatureValueType.Nullable<*> }
    }

    require(expectedButNotComputedDescriptionDeclaration.isEmpty()) {
      """
        Some expected features were not computed
          
          Features ${expectedButNotComputedDescriptionDeclaration.map { it.name }}
          were expected to be computed by $descriptor, because was declared and accepted by the feature filter.

          If the features are nullable, you should mark the declarations as .nullable(),
          and then either put the 'null' to the result set explicitly, or mark the corresponding
          descriptor's descriptionPolicy as 'putNullImplicitly'.

      """.trimIndent()
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

  private suspend fun describeTier(tier: Tier<*>, tierDescriptors: List<TierDescriptor>, environment: Environment): DescriptionPartition {
    val toComputeFilter = createFilterOfFeaturesToCompute(tier, tierDescriptors)
    val usefulTierDescriptors = tierDescriptors.filter { it.couldBeUseful(toComputeFilter) }
    thisLogger().debug {
      """
        [${mlTask.name}] Describing $tier
         - Usable descriptors: $usefulTierDescriptors
         - Not usable descriptors: ${tierDescriptors.filterNot { it in usefulTierDescriptors }}
      """.trimIndent()
    }
    val description: Map<TierDescriptor, Set<Feature>> = descriptionComputer.computeDescription(
      tier,
      usefulTierDescriptors,
      environment,
      toComputeFilter
    )
    thisLogger().debug {
      "[${mlTask.name}] Computed description of $tier\n" +
      description.entries.joinToString("\n") { (descriptor, features) -> "\t- ${descriptor.javaClass.simpleName}: ${features.map { it.toCompactString() }}" }
    }

    val usedByModelFilter = createFilterOfUsedFeatures(tier)

    val descriptionPartition =
      description.entries
        .map { (tierDescriptor, computedDescription) ->
          val redundanciesFreeDescription = removeRedundantDescription(tierDescriptor, computedDescription)
          validateDescription(tierDescriptor, redundanciesFreeDescription, toComputeFilter)
          makeDescriptionPartition(tierDescriptor, redundanciesFreeDescription, usedByModelFilter)
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
