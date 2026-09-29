// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.util.Collections
import java.util.TreeMap

/** One plan entry, paired with the product whose plan holds it. */
internal class ProductDescriptorEntry(
  @JvmField val product: String,
  @JvmField val entry: PluginDescriptorEntry,
)

/**
 * The residue classes of one (plugin, variant): the entries of every product that plans it, grouped by
 * [descriptorEntryState].
 *
 * A residue is what a plugin's patched descriptor needs and the convention does not give. Two products whose entries
 * state one residue are one class, and the class shares one home. The baseline class holds the earliest product of the
 * product order and shares the plugin's own home, so that home never moves when a later product joins. An entry outside
 * the baseline class is divergent. Its class shares one product home under `build/dev-dist-descriptors/<module>/<product>`,
 * named after the first product of the class by the same rule, see [home].
 *
 * The product mode is no residue. Every product's entry of one plugin states the same [PluginDescriptorEntry.modeRefusedContentModules],
 * and a product of another mode refuses no plugin module at build time, so a frontend product is in the class of its
 * monolith unless another fact differs.
 */
internal class DescriptorResidueClasses(
  /** The classes, the baseline class first. Every class holds at least one entry, in product order. */
  @JvmField val classes: List<List<ProductDescriptorEntry>>,
) {
  init {
    require(classes.isNotEmpty() && classes.all { it.isNotEmpty() }) { "A residue class holds at least one entry" }
  }

  /** The entry of the baseline class. Every entry of that class has the same state, so the first one stands for all. */
  val baseline: PluginDescriptorEntry
    get() = classes.first().first().entry

  /** Every entry outside the baseline class, in class order and then in product order. */
  val divergent: List<ProductDescriptorEntry>
    get() = classes.drop(1).flatten()

  /** Whether [product] plans the plugin. */
  fun plans(product: String): Boolean = classes.any { entries -> entries.any { it.product == product } }

  /** Whether [product] is in the baseline class. A product that does not plan the plugin is refused. */
  fun isBaseline(product: String): Boolean {
    for ((index, entries) in classes.withIndex()) {
      if (entries.any { it.product == product }) {
        return index == 0
      }
    }
    error("Product '$product' does not plan '${planEntryKey(baseline)}'")
  }

  /**
   * The product whose product home [product] reads: `null` in the baseline class, which reads the plugin's own home, and
   * the first product of its class otherwise. Every product of one divergent class states one residue, so one leaf
   * serves them all. A product refused by [isBaseline] is refused here too.
   */
  fun home(product: String): String? {
    for ((index, entries) in classes.withIndex()) {
      if (entries.any { it.product == product }) {
        return if (index == 0) null else entries.first().product
      }
    }
    error("Product '$product' does not plan '${planEntryKey(baseline)}'")
  }
}

/**
 * Groups the entries of one (plugin, variant) into [DescriptorResidueClasses].
 *
 * [productOrder] is the map order of [DevDistHalf.splitDistributions]. The class that holds the earliest product of that order
 * is the baseline. A product outside the order sorts after every product in it, by name.
 */
internal fun descriptorResidueClasses(
  entries: List<ProductDescriptorEntry>,
  productOrder: Collection<String>,
): DescriptorResidueClasses {
  require(entries.isNotEmpty()) { "A residue class needs at least one entry" }
  val key = planEntryKey(entries.first().entry)
  require(entries.all { planEntryKey(it.entry) == key }) { "The entries of '$key' name two plan entry keys" }
  val rank = productOrder.withIndex().associate { (index, product) -> product to index }
  val ordered = entries.sortedWith(compareBy({ rank.get(it.product) ?: Int.MAX_VALUE }, { it.product }))
  require(ordered.map { it.product }.distinct().size == ordered.size) { "Two plans of one product state '$key'" }
  val classes = LinkedHashMap<List<Any?>, MutableList<ProductDescriptorEntry>>()
  for (entry in ordered) {
    classes.computeIfAbsent(descriptorEntryState(entry.entry)) { ArrayList() }.add(entry)
  }
  return DescriptorResidueClasses(classes.values.map { Collections.unmodifiableList(it) })
}

/**
 * The residue classes of every (plugin, variant) of [plans], keyed by [planEntryKey] and sorted.
 *
 * The plans arrive sorted by product name, and the rule reads [productOrder] instead, so the sorted order of the plans
 * never decides a baseline.
 */
internal fun computeDescriptorResidueClasses(plans: List<PluginDescriptorPlan>, productOrder: Collection<String>): Map<String, DescriptorResidueClasses> {
  val entriesByKey = TreeMap<String, MutableList<ProductDescriptorEntry>>()
  for (plan in plans) {
    for (entry in plan.plugins) {
      entriesByKey.computeIfAbsent(planEntryKey(entry)) { ArrayList() }.add(ProductDescriptorEntry(plan.platformPrefix, entry))
    }
  }
  val result = TreeMap<String, DescriptorResidueClasses>()
  for ((key, entries) in entriesByKey) {
    result.put(key, descriptorResidueClasses(entries, productOrder))
  }
  return Collections.unmodifiableMap(result)
}

/** Every fact of an entry the leaf states, so two entries with one state render one leaf text. */
internal fun descriptorEntryState(entry: PluginDescriptorEntry): List<Any?> {
  return listOf(
    entry.mainModule, entry.variant, entry.moduleTarget, entry.descriptor, entry.descriptorInTestOutput,
    entry.refusedContentModules.toList(), entry.separateJar.toList(),
    entry.descriptors.map { listOf(it.loadPath, it.label, it.testOutput, it.moduleName) },
    entry.includeDescriptors.map { it.loadPath to it.relativePath },
    entry.libraryDescriptorRows.map { listOf(it.loadPath, it.moduleName, it.libraryName) },
    entry.libraryDescriptors.map { it.loadPath to it.containerLabel },
    entry.markers.toList(), entry.versionSuffix, entry.compatibleBuildRange, entry.derivesOsArchStamps, entry.embedsContentModules,
    entry.exactVersion, entry.retainProductDescriptor, entry.directoryName,
    entry.contentModules.map { listOf(it.name, it.isOptional, it.loading) },
    entry.modeRefusedContentModules.toSortedMap().mapValues { it.value.toList() },
  )
}

/**
 * The marker rows this entry states, which is none for a variant of one operating system and one architecture.
 *
 * Such a variant gives its own row, and the plan verified the layout against the derived one. So a row here would be a
 * checked-in copy of a convention, and the central plan file follows the same rule.
 */
internal fun statedMarkers(entry: PluginDescriptorEntry): List<String> {
  return if (entry.derivesOsArchStamps) emptyList() else entry.markers
}

/** The version suffix this entry states, which is none for the variant [statedMarkers] states no row for. */
internal fun statedVersionSuffix(entry: PluginDescriptorEntry): String {
  return if (entry.derivesOsArchStamps) "" else entry.versionSuffix
}
