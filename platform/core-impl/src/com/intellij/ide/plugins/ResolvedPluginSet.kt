// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

/**
 * A plugin set with all constraints resolved. This includes dependencies, so dependency graph information and a [RuntimeModuleGroupGraph] is available.
 *
 * Methods that accept a descriptor throw [IllegalArgumentException] if that descriptor is not part of the [candidateSet].
 */
@ApiStatus.Internal
interface ResolvedPluginSet {
  /**
   * The input plugin set the constraint resolution was performed on.
   * Every module/descriptor of every plugin from [candidateSet] is either contained in [sortedResolvedDescriptors] or is associated
   * with an [exclusion reason][getExclusionReason].
   */
  val candidateSet: UnambiguousPluginSet

  /**
   * The input initialization context.
   */
  val initContext: PluginInitializationContext

  /**
   * A descriptor is resolved when all its loading constraints are satisfied,
   * e.g., the content module loading depends on the main plugin descriptor loading; all dependencies are resolved too; etc.
   *
   * If a descriptor from the [candidateSet] is not resolved, [getExclusionReason] returns a non-null reason for such a descriptor.
   *
   * The rule of thumb for the ordering is that the descriptor's dependencies come before the descriptor.
   */
  val sortedResolvedDescriptors: Set<IdeaPluginDescriptorImpl>

  /**
   * Represents a graph built by a mapping from [sortedResolvedDescriptors] into [RuntimeModuleGroup]s (that can be turned into classloaders).
   */
  val runtimeModuleGroupGraph: RuntimeModuleGroupGraph

  /**
   * Returns an exclusion reason for a given descriptor (or module). If it's `null`, the descriptor is resolved.
   *
   * @throws IllegalArgumentException if provided [descriptor] is not part of the [candidateSet]
   */
  fun getExclusionReason(descriptor: IdeaPluginDescriptorImpl): DescriptorExclusionReason?

  /**
   * Returns a list of resolved descriptors (incl. modules) the given [resolvedDescriptor] directly depends on.
   * The list contains the final effective set of descriptor's dependencies.
   *
   * Returned list can contain [DependsSubDescriptor] only if the [resolvedDescriptor] is [DependsSubDescriptor] itself (it implicitly depends on the parent).
   *
   * @throws IllegalArgumentException if provided [resolvedDescriptor] is not resolved or is not part of the [candidateSet]
   */
  fun getDirectResolvedDependencies(resolvedDescriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl>

  /**
   * Returns a list of resolved descriptors (incl. modules) that directly depend on the given [resolvedDescriptor].
   * The list contains the final effective set of dependents in the context of the current [ResolvedPluginSet].
   *
   * Returned list can contain [DependsSubDescriptor] only if the [resolvedDescriptor] is [DependsSubDescriptor] itself (it implicitly depends on the parent).
   */
  fun getDirectResolvedDependents(resolvedDescriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl>
}

@ApiStatus.Internal
fun ResolvedPluginSet.isResolved(descriptor: IdeaPluginDescriptorImpl): Boolean = descriptor in sortedResolvedDescriptors

@ApiStatus.Internal
fun ResolvedPluginSet.isExcluded(descriptor: IdeaPluginDescriptorImpl): Boolean = getExclusionReason(descriptor) != null
