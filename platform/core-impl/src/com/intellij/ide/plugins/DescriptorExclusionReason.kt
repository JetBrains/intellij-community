// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
sealed interface DescriptorExclusionReason {
  val descriptor: IdeaPluginDescriptorImpl
}

@ApiStatus.Internal
sealed interface ChainedExclusion {
  val precedingExcludedDescriptor: IdeaPluginDescriptorImpl
}

@ApiStatus.Internal
class DependencyIsNotResolved(
  override val descriptor: IdeaPluginDescriptorImpl,
  val dependency: PluginDependencyAnalysis.DependencyRef,
) : DescriptorExclusionReason

@ApiStatus.Internal
class DependencyIsExcluded(
  override val descriptor: IdeaPluginDescriptorImpl,
  val dependencyModule: PluginModuleDescriptor,
) : DescriptorExclusionReason, ChainedExclusion {
  override val precedingExcludedDescriptor: IdeaPluginDescriptorImpl get() = dependencyModule
}

@ApiStatus.Internal
class DependencyIsNotVisible(
  override val descriptor: IdeaPluginDescriptorImpl,
  val dependencyModule: PluginModuleDescriptor,
  val visibilityViolationLogMessage: String,
) : DescriptorExclusionReason


@ApiStatus.Internal
class DependsParentIsExcluded(
  override val descriptor: DependsSubDescriptor,
) : DescriptorExclusionReason, ChainedExclusion {
  override val precedingExcludedDescriptor: IdeaPluginDescriptorImpl
    get() = descriptor.parent
}

@ApiStatus.Internal
class ContentModuleParentIsExcluded(
  override val descriptor: ContentModuleDescriptor,
) : DescriptorExclusionReason, ChainedExclusion {
  override val precedingExcludedDescriptor: IdeaPluginDescriptorImpl
    get() = descriptor.parent
}

@ApiStatus.Internal
class RequiredContentModuleIsExcluded(
  override val descriptor: PluginMainDescriptor,
  val excludedContentModule: ContentModuleDescriptor,
) : DescriptorExclusionReason, ChainedExclusion {
  override val precedingExcludedDescriptor: IdeaPluginDescriptorImpl
    get() = excludedContentModule
}

@ApiStatus.Internal
class OnDemandContentModuleHasNoDependentsLeft(
  override val descriptor: ContentModuleDescriptor,
) : DescriptorExclusionReason

@ApiStatus.Internal
class PartOfDependencyCycle(
  override val descriptor: IdeaPluginDescriptorImpl,
  val dependencyCycle: DependencyCycleInfo<IdeaPluginDescriptorImpl>,
) : DescriptorExclusionReason

@ApiStatus.Internal
class PartOfRuntimeModuleGroupDependencyCycle(
  override val descriptor: IdeaPluginDescriptorImpl,
  val dependencyCycle: DependencyCycleInfo<RuntimeModuleGroup>,
) : DescriptorExclusionReason

@ApiStatus.Internal
class PluginVersionIsSuperseded(
  override val descriptor: PluginMainDescriptor,
  val supersededBy: PluginMainDescriptor,
): DescriptorExclusionReason

@ApiStatus.Internal
class PluginIsIncompatibleWithProduct(
  override val descriptor: PluginMainDescriptor,
  val incompatibilityReason: PluginIncompatibilityReason,
) : DescriptorExclusionReason

@ApiStatus.Internal
class IncompatibleWithAnotherModule(
  override val descriptor: IdeaPluginDescriptorImpl,
  val preferredIncompatibleModule: PluginModuleDescriptor,
) : DescriptorExclusionReason

@ApiStatus.Internal
class PackagePrefixConflictWithAnotherModule(
  override val descriptor: PluginModuleDescriptor,
  val preferredConflictingModule: PluginModuleDescriptor,
) : DescriptorExclusionReason

/**
 * Exactly one of [conflictingPluginId] or [conflictingModuleId] is not null
 */
@ApiStatus.Internal
class PluginDeclaresConflictingId(
  override val descriptor: PluginMainDescriptor,
  val declarationOrigin: PluginModuleDescriptor,
  val conflictingModule: PluginModuleDescriptor,
  val conflictingPluginId: PluginId? = null,
  val conflictingModuleId: PluginModuleId? = null,
) : DescriptorExclusionReason {
  init {
    require((conflictingPluginId != null) != (conflictingModuleId != null))
  }

  val conflictingId: Any get() = conflictingPluginId ?: conflictingModuleId!!
}

@ApiStatus.Internal
class PluginIsMarkedDisabled(
  override val descriptor: PluginMainDescriptor,
) : DescriptorExclusionReason

@ApiStatus.Internal
class ExcludedByEnvironmentConfiguration(
  override val descriptor: PluginModuleDescriptor,
  val reason: EnvironmentDependentModuleUnavailabilityReason,
) : DescriptorExclusionReason

@ApiStatus.Internal
class ProductRulesImposedExclusion(
  override val descriptor: PluginModuleDescriptor,
  val productReason: ProductRulesImposedExclusionReason,
) : DescriptorExclusionReason {
  interface ProductRulesImposedExclusionReason {
    fun getLogMessage(): String
  }
}

@ApiStatus.Internal
fun DescriptorExclusionReason.getPrecedingLinkInExclusionChain(): IdeaPluginDescriptorImpl? =
  (this as? ChainedExclusion)?.precedingExcludedDescriptor

@ApiStatus.Internal
fun IdeaPluginDescriptorImpl.sequenceDescriptorExclusionChain(
  getExclusionReason: (IdeaPluginDescriptorImpl) -> DescriptorExclusionReason?,
): Sequence<IdeaPluginDescriptorImpl> {
  return sequence {
    var current: IdeaPluginDescriptorImpl? = this@sequenceDescriptorExclusionChain
    while (current != null) {
      val reason = getExclusionReason(current)
                   ?: break
      yield(current)
      current = reason.getPrecedingLinkInExclusionChain()
    }
  }
}

@ApiStatus.Internal
class DependencyCycleInfo<N>(
  val nodesWithDependenciesOnCycle: Map<N, List<N>>
)
