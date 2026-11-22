// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus

/**
 * A layout fact the distribution plan cannot state, found by the layout bindings or the descriptor plan.
 *
 * The run stops on it for a bundled and an additional plugin alike. The message names the plugin and the fact.
 */
@ApiStatus.Internal
class DevDistUnplannableLayoutException(message: String) : IllegalStateException(message)

/** The stage of the plan that refuses an update plugin, in the order of the run. */
@ApiStatus.Internal
enum class DevDistUpdateRefusalStage(@JvmField val title: String) {
  ENUMERATION("request enumeration"),
  LAYOUT_BINDINGS("layout bindings"),
  DESCRIPTOR_PLAN("descriptor plan"),
}

/**
 * The refusals of the plugins that only the update set of a product names, see [DevDistPluginRequest.updateOnly].
 *
 * Each stage of the plan adds the refusals that it finds, and a refused plugin leaves the plan before the next stage. So
 * one run finds every update plugin that the plan cannot state, and [check] names all of them at once. The first
 * refusal of a plugin of a product wins. The collector takes concurrent callers.
 */
@ApiStatus.Internal
class DevDistUpdateRefusals {
  private val refusals = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Pair<DevDistUpdateRefusalStage, String>>()

  /** Adds the refusal of [mainModule] of [product] at [stage] with [reason], unless an earlier stage refused it. */
  fun add(product: String, mainModule: String, stage: DevDistUpdateRefusalStage, reason: String) {
    refusals.merge(product to mainModule, stage to reason) { earlier, later -> if (later.first < earlier.first) later else earlier }
  }

  /** Whether a stage refused [mainModule] of [product]. */
  fun isRefused(product: String, mainModule: String): Boolean = refusals.containsKey(product to mainModule)

  /** The refused plugins of [product], sorted. */
  fun refused(product: String): Set<String> = refusals.keys.filter { it.first == product }.mapTo(java.util.TreeSet()) { it.second }

  /**
   * Fails once when a refused plugin is no hold-out, or when a hold-out is stale. [holdOuts] are the hold-outs of each
   * updater product, see [SplitDevDistribution.updatePluginHoldOuts]. [updatePluginModules] are the update plugins of
   * each product, hold-outs included. A hold-out is stale when no stage refused it, or when it is no update plugin.
   */
  fun check(holdOuts: Map<String, Map<String, String>>, updatePluginModules: Map<String, List<String>>) {
    val lines = ArrayList<String>()
    val sorted = refusals.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
    for ((key, value) in sorted) {
      val (product, mainModule) = key
      if (holdOuts.get(product)?.containsKey(mainModule) != true) {
        lines.add("  $product $mainModule (${value.first.title}): ${value.second}")
      }
    }
    val stale = ArrayList<String>()
    for ((product, ofProduct) in holdOuts) {
      val modules = updatePluginModules.get(product).orEmpty()
      for (mainModule in ofProduct.keys.sorted()) {
        if (mainModule !in modules) {
          stale.add("  $product $mainModule: it is no update plugin of the product")
        }
        else if (!isRefused(product, mainModule)) {
          stale.add("  $product $mainModule: the plan states it")
        }
      }
    }
    if (lines.isEmpty() && stale.isEmpty()) {
      return
    }
    val message = StringBuilder()
    if (lines.isNotEmpty()) {
      message.append("The plan cannot state these update plugins (${lines.size}). Fix each one, or hold it out with a reason in ")
        .append("`updatePluginHoldOuts` of its split product:\n")
      lines.joinTo(message, separator = "\n")
    }
    if (stale.isNotEmpty()) {
      if (message.isNotEmpty()) message.append('\n')
      message.append("Remove these stale hold-outs (${stale.size}) from `updatePluginHoldOuts`:\n")
      stale.joinTo(message, separator = "\n")
    }
    throw DevDistUnplannableLayoutException(message.toString())
  }
}
