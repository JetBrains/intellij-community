// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * The product mode of the current process.
 *
 * The initial mode comes from the `intellij.platform.product.mode` system property.
 * A process may then move to another mode without a restart, which is how IJ Light gains a full RD/Monolith functionality.
 * [transitionTo] performs the move, and [TRANSITIONS] declares which moves are legal.
 *
 * Read the mode from product code through [com.intellij.platform.ide.productMode.IdeProductMode].
 * That service needs no application, but it does need the platform. Use this object directly only
 * from code that runs before or below the application, such as the plugin subsystem.
 */
@ApiStatus.Internal
object CurrentProductMode {
  private val state = MutableStateFlow(computeInitialProductMode())

  val value: ProductMode
    get() = state.value

  /** Emits the current mode, and a new value on every completed [transitionTo]. */
  val flow: StateFlow<ProductMode>
    get() = state

  /** The modes that [from] may move to in one step. */
  @VisibleForTesting
  fun allowedTargets(from: ProductMode): Set<ProductMode> = TRANSITIONS[from].orEmpty()

  /**
   * Moves the process into [target].
   *
   * Returns `false` when [TRANSITIONS] does not allow the move from the current mode. A second call
   * with the same target therefore returns `false` and does nothing, because the first call already
   * moved the mode. A caller that loads plugins for the new mode must treat `false` as "already
   * done" and skip that work.
   */
  fun transitionTo(target: ProductMode): Boolean {
    while (true) {
      val current = state.value
      if (target !in allowedTargets(current)) {
        return false
      }
      if (state.compareAndSet(current, target)) {
        logger<CurrentProductMode>().info("Product mode moved from '${current.id}' to '${target.id}'")
        return true
      }
    }
  }

  @TestOnly
  fun <R> withProductMode(mode: ProductMode, body: () -> R): R {
    val previous = state.value
    state.value = mode
    try {
      return body()
    }
    finally {
      state.value = previous
    }
  }

  private fun computeInitialProductMode(): ProductMode {
    val explicitValue = System.getProperty(PRODUCT_MODE_PROPERTY)
    if (explicitValue != null) {
      val mode = ProductMode.findById(explicitValue)
                 ?: error("Unknown mode '$explicitValue' specified in '$PRODUCT_MODE_PROPERTY' system property")
      logger<CurrentProductMode>().info("Product mode '${mode.id}' comes from the '$PRODUCT_MODE_PROPERTY' system property")
      return mode
    }
    // No property, so infer the mode. A distribution that uses the modular loader always sets the
    // property, so only a dev build or a run from sources reaches this branch.
    val mode = when {
      AppMode.isRemoteDevHost() -> ProductMode.BACKEND
      PlatformUtils.isJetBrainsClient() -> ProductMode.FRONTEND
      else -> ProductMode.MONOLITH
    }
    logger<CurrentProductMode>().info("Product mode '${mode.id}' is inferred; '$PRODUCT_MODE_PROPERTY' is not set")
    return mode
  }

  private val TRANSITIONS: Map<ProductMode, Set<ProductMode>> = mapOf(
    ProductMode.LIGHT to setOf(ProductMode.LIGHT_WITH_RD_CONNECTION),
    ProductMode.LIGHT_WITH_RD_CONNECTION to setOf(ProductMode.FRONTEND),
  )

  private const val PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"
}
