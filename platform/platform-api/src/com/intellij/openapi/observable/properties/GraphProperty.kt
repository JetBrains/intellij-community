// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

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
interface GraphProperty<T> : ObservableClearableProperty<T> {

  @JvmDefault
  @Deprecated("Use dependsOn with update", ReplaceWith("this.dependsOn(parent) { this.reset(); this.get() }"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun dependsOn(parent: ObservableClearableProperty<*>) {
    dependsOn(parent) { reset(); get() }
  }

  @JvmDefault
  @Deprecated("Please recompile code", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun dependsOn(parent: ObservableClearableProperty<*>, update: () -> T) {
    dependsOn(parent, update)
  }

  /**
   * @see PropertyGraph.dependsOn
   */
  @JvmDefault
  fun dependsOn(parent: ObservableProperty<*>, update: () -> T) {
    logger<GraphProperty<*>>().error("Please, implement this method directly.")
  }

  /**
   * @see PropertyGraph.afterPropagation
   */
  fun afterPropagation(listener: () -> Unit)
}