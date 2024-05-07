// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureFilter
import org.jetbrains.annotations.ApiStatus


/**
 * A [TierDescriptor] that is not fully aware of the features it describes with.
 * It is used to make a smooth transition from the old forms of features providers to the new API.
 */
@ApiStatus.Internal
interface ObsoleteTierDescriptor : TierDescriptor {
  /**
   * The case when a [TierDescriptor] is an [ObsoleteTierDescriptor] is handled in the API
   * individually each time. And in those times, we cannot rely on the [descriptionDeclaration],
   * because it may not be correct.
   */
  override val descriptionDeclaration: Set<FeatureDeclaration<*>>
    get() = throw IllegalAccessError("Obsolete descriptor does not provide a description declaration")

  override fun couldBeUseful(usefulFeaturesFilter: FeatureFilter): Boolean {
    return true
  }

  /**
   * The declaration that is already known.
   * If there is a feature that is computed but not declared, then they can be logged,
   * so you can add them to the declaration.
   *
   * Turn on the ml.description.logMissing registry key to log the missing features.
   */
  val partialDescriptionDeclaration: Set<FeatureDeclaration<*>>
    get() = emptySet()
}
