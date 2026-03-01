// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

/**
 * Category of compute node - determines whether it produces output files or only validates.
 */
internal enum class NodeCategory {
  /** Nodes that produce output files (XML, dependencies, etc.) */
  GENERATION,
  /** Nodes that only validate (no file output) */
  VALIDATION
}

/**
 * Unique identifier for a compute node with category information.
 *
 * The category is used for filtering (e.g., run only specific validation rules).
 */
internal data class NodeId(
  @JvmField val name: String,
  @JvmField val category: NodeCategory
) {
  override fun toString(): String = name
}

/**
 * Standard node IDs for slot-based dependency resolution.
 *
 * These constants ensure consistent ID usage across [PipelineNode] implementations.
 * Each ID carries category information for filtering (e.g., run only specific validation rules).
 *
 * **Note:** Dependencies are now inferred from [PipelineNode.requires] and [PipelineNode.produces]
 * slot declarations, not from explicit `runAfter` sets.
 */
internal object NodeIds {
  // ============ Generation (produce output files) ============

  /** Module set XML file generation */
  @JvmField val MODULE_SET_XML = NodeId("moduleSetXml", NodeCategory.GENERATION)

  /** Product module dependency generation (modules in module sets) */
  @JvmField val PRODUCT_MODULE_DEPS = NodeId("productModuleDeps", NodeCategory.GENERATION)

  /** Content module dependency planning (all content modules including test descriptors) */
  @JvmField val CONTENT_MODULE_DEPS = NodeId("contentModuleDeps", NodeCategory.GENERATION)

  /** Content module XML writing */
  @JvmField val CONTENT_MODULE_XML_WRITE = NodeId("contentModuleXmlWrite", NodeCategory.GENERATION)

  /** Plugin.xml dependency planning */
  @JvmField val PLUGIN_XML_DEPS = NodeId("pluginXmlDeps", NodeCategory.GENERATION)

  /** Plugin.xml writing */
  @JvmField val PLUGIN_XML_WRITE = NodeId("pluginXmlWrite", NodeCategory.GENERATION)

  /** Product XML file generation */
  @JvmField val PRODUCT_XML = NodeId("productXml", NodeCategory.GENERATION)

  /** Test plugin XML file generation */
  @JvmField val TEST_PLUGIN_XML = NodeId("testPluginXml", NodeCategory.GENERATION)

  /** Test plugin dependency planning */
  @JvmField val TEST_PLUGIN_DEPENDENCY_PLAN = NodeId("testPluginDependencyPlan", NodeCategory.GENERATION)

  /** Suppression config generation (collects implicit dependencies) */
  @JvmField val SUPPRESSION_CONFIG = NodeId("suppressionConfig", NodeCategory.GENERATION)

  // ============ Validation (only validate, no file output) ============

  /** Plugin validation (runs after all plugin-related generators) */
  @JvmField val PLUGIN_VALIDATION = NodeId("pluginValidation", NodeCategory.VALIDATION)

  /** Plugin content structural validation (loading mode constraints) */
  @JvmField val PLUGIN_CONTENT_STRUCTURE_VALIDATION = NodeId("pluginContentStructureValidation", NodeCategory.VALIDATION)

  /** Content module plugin dependency validation (IML deps -> XML plugin deps) */
  @JvmField val CONTENT_MODULE_PLUGIN_DEPENDENCY_VALIDATION = NodeId("contentModulePluginDependencyValidation", NodeCategory.VALIDATION)

  /** Content module backing (module -> target -> JPS) validation */
  @JvmField val CONTENT_MODULE_BACKING_VALIDATION = NodeId("contentModuleBackingValidation", NodeCategory.VALIDATION)

  /** Plugin-to-plugin dependency validation */
  @JvmField val PLUGIN_PLUGIN_VALIDATION = NodeId("pluginPluginValidation", NodeCategory.VALIDATION)

  /** Duplicate legacy/modern plugin dependency declaration validation */
  @JvmField val PLUGIN_DEPENDENCY_DECLARATION_VALIDATION = NodeId("pluginDependencyDeclarationValidation", NodeCategory.VALIDATION)

  /** Test plugin plugin dependency validation */
  @JvmField val TEST_PLUGIN_PLUGIN_DEPENDENCY_VALIDATION = NodeId("testPluginPluginDependencyValidation", NodeCategory.VALIDATION)

  /** Suppression config validation */
  @JvmField val SUPPRESSION_CONFIG_VALIDATION = NodeId("suppressionConfigValidation", NodeCategory.VALIDATION)

  /** Self-contained module set validation */
  @JvmField val SELF_CONTAINED_VALIDATION = NodeId("selfContainedValidation", NodeCategory.VALIDATION)

  /** Product module set validation */
  @JvmField val PRODUCT_MODULE_SET_VALIDATION = NodeId("productModuleSetValidation", NodeCategory.VALIDATION)

  /** Library module validation (auto-fixes .iml files) */
  @JvmField val LIBRARY_MODULE_VALIDATION = NodeId("libraryModuleValidation", NodeCategory.VALIDATION)

  /** Plugin content module JPS dependency validation */
  @JvmField val PLUGIN_CONTENT_MODULE_VALIDATION = NodeId("pluginContentModuleValidation", NodeCategory.VALIDATION)

  /** Duplicate content modules across bundled plugins */
  @JvmField val PLUGIN_CONTENT_DUPLICATE_VALIDATION = NodeId("pluginContentDuplicateValidation", NodeCategory.VALIDATION)

  /** Conflicting descriptor IDs between production and test plugins */
  @JvmField val PLUGIN_DESCRIPTOR_ID_CONFLICT_VALIDATION = NodeId("pluginDescriptorIdConflictValidation", NodeCategory.VALIDATION)

  /** Test library scope validation */
  @JvmField val TEST_LIBRARY_SCOPE_VALIDATION = NodeId("testLibraryScopeValidation", NodeCategory.VALIDATION)
}
