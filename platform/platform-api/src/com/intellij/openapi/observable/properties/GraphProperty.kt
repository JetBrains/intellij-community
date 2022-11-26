// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus

/**
 * [GraphProperty] is builder of [PropertyGraph].
 *
 * @see PropertyGraph
 * @see ObservableProperty
 * @see PropertyGraph.property
 */
@Suppress("DEPRECATION")
@ApiStatus.NonExtendable
interface GraphProperty<T> : ObservableClearableProperty<T> {

  /**
   * @see PropertyGraph.dependsOn
   */
  fun dependsOn(parent: ObservableProperty<*>, update: () -> T) {
    logger<GraphProperty<*>>().error("Please, implement this method directly.")
  }

  /**
   * @see PropertyGraph.afterPropagation
   */
  fun afterPropagation(listener: () -> Unit)

  /**
   * @see PropertyGraph.afterPropagation
   */
  fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) {
    afterPropagation(listener)
  }

  @Deprecated("Use set instead")
  @ApiStatus.ScheduledForRemoval
  override fun reset() {}

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit) {}

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {}

  @Deprecated("Use dependsOn with update", ReplaceWith("this.dependsOn(parent) { this.reset(); this.get() }"))
  @ApiStatus.ScheduledForRemoval
  fun dependsOn(parent: ObservableClearableProperty<*>) {
    dependsOn(parent) { reset(); get() }
  }

  @Deprecated("Please recompile code", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  fun dependsOn(parent: ObservableClearableProperty<*>, update: () -> T) {
    dependsOn(parent, update)
  }
}