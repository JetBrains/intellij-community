// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesIde
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesKtor
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesMisc
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesPlatform
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesTemporaryBundled
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreIde
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreLang
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.corePlatform
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.essential
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.essentialMinimal
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.fleet
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcBackend
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcMinimal

/**
 * Core platform module sets forming the foundation of IntelliJ products.
 *
 * This file contains the base module sets that provide the platform infrastructure:
 * - **corePlatform**: Base platform without IDE (for analysis tools)
 * - **coreIde**: Platform + basic IDE functionality
 * - **coreLang**: Platform + IDE + language support
 * - **essentialMinimal**: Lightweight IDE with editing
 * - **essential**: Full essential IDE with debugging and navigation
 * - **debugger**: Debugger platform
 * - **rpc/rpcMinimal**: RPC infrastructure
 *
 * **Separated from CommunityModuleSets** to reduce file size and improve organization.
 * Library module sets remain in CommunityModuleSets.
 */
object CoreModuleSets {
  /**
   * Core platform modules without IDE or language support.
   * Contains base infrastructure for analysis and inspection tools.
   *
   * **Use when:** Building analysis/inspection tools that only need platform APIs and extension points
   *
   * **Example products:**
   * - **CodeServer**: Analysis and inspection tool without IDE features
   * - Other code analysis tools that only need platform APIs
   *
   * **Don't use for:**
   * - Products needing IDE functionality → Use `coreIde()` instead
   * - Products needing language support → Use `coreLang()` or `essentialMinimal()`
   * - IDE products with editing capabilities → Use `essentialMinimal()` instead
   *
   * @see coreIde for platform with basic IDE functionality
   * @see coreLang for platform with IDE and language support
   * @see essentialMinimal for lightweight IDE with editing (most IDE products should use this)
   */
  fun corePlatform(): ModuleSet = moduleSet("core.platform", selfContained = true, outputModule = "intellij.platform.ide.core", includeDependencies = true) {
    moduleSet(librariesPlatform())

    embeddedModule("intellij.platform.util.ex")
    embeddedModule("intellij.platform.util.ui")

    embeddedModule("intellij.platform.core")
    embeddedModule("intellij.platform.core.ui")
    embeddedModule("intellij.platform.core.impl")

    embeddedModule("intellij.platform.projectModel")
    embeddedModule("intellij.platform.projectModel.impl")

    // Analysis modules needed by core platform modules
    embeddedModule("intellij.platform.analysis")
    embeddedModule("intellij.platform.analysis.impl")

    // Include minimal RPC infrastructure AFTER core platform modules
    // (kernel depends on platform.core, so core must be available first)
    moduleSet(rpcMinimal())

    embeddedModule("intellij.platform.ide.core")
  }

  /**
   * Core platform with basic IDE functionality.
   * Adds IDE modules on top of platform infrastructure without language support.
   *
   * **Contents:**
   * - `corePlatform()` (nested) - Base platform infrastructure
   * - IDE module: intellij.platform.ide
   *
   * **Use when:** Building products that need IDE features but not language support
   *
   * **Architecture note:** This bridges the gap between pure platform (corePlatform) and
   * full language-enabled IDE (coreLang). Most analysis tools won't need this.
   *
   * @see corePlatform for platform without IDE functionality
   * @see coreLang for IDE with language support
   */
  fun coreIde(): ModuleSet = moduleSet("core.ide", includeDependencies = true) {
    // Include core platform (util, core, projectModel, analysis, ide.core, kernel)
    moduleSet(corePlatform())

    // Add IDE-specific libraries (UI, terminal, browser, SSH)
    moduleSet(librariesIde())

    // Add basic IDE functionality on top of platform
    embeddedModule("intellij.platform.ide")
  }

  /**
   * Language support and IDE implementation modules for IntelliJ Platform.
   * Builds on top of `coreIde()` to provide language features and IDE implementation.
   *
   * **Contents:**
   * - `coreIde()` (nested) - Includes corePlatform + intellij.platform.ide
   * - Language modules: lang.core, lang, lang.impl
   * - IDE implementation: ide.impl (placed here because it depends on lang.core)
   * - Additional dependencies: eel.impl, diff.impl, fleet.andel
   *
   * **Architecture note:** `ide.impl` is in this module set (not in coreIde) because
   * it depends on `lang.core`. This resolves the circular dependency:
   * coreIde → lang.core → ide.impl (all in proper order).
   *
   * **Use when:** Building products that need language support and IDE features but not
   * the full essentialMinimal infrastructure (editor, search, RPC, backend/frontend split).
   *
   * **⚠️ WARNING:** Most products should use `essentialMinimal()` instead, which includes
   * this module set plus essential IDE infrastructure (editor, search, RPC).
   *
   * Only use this directly if you need language features but want to exclude editor/search/RPC modules.
   *
   * **Products using this:** All products via `essentialMinimal()` which nests this module set
   *
   * @see coreIde for IDE functionality without language support
   * @see corePlatform for base platform without IDE or language support
   * @see essentialMinimal for full minimal IDE (includes this + RPC + editor + search) - RECOMMENDED
   */
  fun coreLang(): ModuleSet = moduleSet("core.lang", includeDependencies = true) {
    // Include core IDE (corePlatform + intellij.platform.ide)
    moduleSet(coreIde())

    embeddedModule("intellij.platform.lang.core")
    embeddedModule("intellij.platform.lang")
    embeddedModule("intellij.platform.lang.impl")

    // IDE implementation (depends on lang.core, so must come after)
    embeddedModule("intellij.platform.ide.impl")

    // Additional dependencies specific to lang.impl and ide.impl
    embeddedModule("intellij.platform.ide.concurrency")
    embeddedModule("intellij.platform.builtInServer")
    embeddedModule("intellij.platform.externalSystem")
    embeddedModule("intellij.platform.eel.impl")
    embeddedModule("intellij.platform.diff")
    embeddedModule("intellij.platform.diff.impl")
    embeddedModule("fleet.andel")

    // Temporary: lang.impl incorrectly depends on xstream (should be removed)
    moduleSet(librariesTemporaryBundled())
  }

  /**
   * Minimal essential platform modules required by lightweight IDE products WITH editing capabilities.
   *
   * **Contents:**
   * - `coreLang()` (nested) - Includes corePlatform + language support + ide.impl
   * - `rpcBackend()` - RPC backend/frontend split and topics (base RPC from corePlatform)
   * - Backend/frontend split modules (settings, backend, project.backend, etc.)
   * - Editor modules (editor, editor.backend)
   * - Search modules (searchEverywhere with backend/frontend)
   * - Inline completion
   *
   * **Use when:** Building lightweight IDE products that provide code editing functionality
   *
   * **Example products:**
   * - **GitClient**: Lightweight VCS IDE with editing - uses `essentialMinimal()` + `vcs()`
   * - **Gateway**: Remote development gateway - uses `essentialMinimal()` + `vcs()` + `ssh()`
   *
   * **Don't use for:**
   * - Analysis-only tools without editing (e.g., CodeServer) → Use `corePlatform()` instead
   *
   * **Hierarchy:**
   * ```
   * essentialMinimal
   *   └─ coreLang
   *       └─ corePlatform
   *           └─ libraries
   * ```
   *
   * **Note:** Most IDE products should start with this module set or `essential()` (which includes this).
   * Nested by `essential()` to avoid duplication.
   *
   * @see essential for full IDE with navigation, debugging, and more features
   * @see coreLang for just language support without editor/search/RPC
   * @see corePlatform for analysis tools without editing
   */
  fun essentialMinimal(): ModuleSet = moduleSet("essential.minimal", includeDependencies = true) {
    // Lang includes corePlatform (which includes librariesPlatform) as nested set
    moduleSet(coreLang())

    // RPC backend functionality (base RPC/kernel already in corePlatform via rpcMinimal)
    moduleSet(rpcBackend())

    // Additional library sets not in corePlatform but needed by essentialMinimal+
    moduleSet(librariesKtor())  // For RPC/Remote Dev
    moduleSet(librariesMisc())  // For specialized uses (XML-RPC, CSV, document store)

    // Credential store (needed by 36 products)
    embeddedModule("intellij.platform.credentialStore.ui")
    embeddedModule("intellij.platform.credentialStore.impl")

    // Core platform backend/frontend split
    module("intellij.platform.settings.local")
    module("intellij.platform.backend")
    module("intellij.platform.project.backend")
    module("intellij.platform.progress.backend")
    module("intellij.platform.lang.impl.backend")

    // Frontend/monolith
    module("intellij.platform.frontend")
    module("intellij.platform.monolith")

    // Editor
    module("intellij.platform.editor")
    module("intellij.platform.editor.backend")

    // Search
    module("intellij.platform.searchEverywhere")
    module("intellij.platform.searchEverywhere.backend")
    module("intellij.platform.searchEverywhere.frontend")

    // Completion
    module("intellij.platform.inline.completion")
  }

  /**
   * Recent files support (both backend and frontend).
   * Provides recently opened files UI and persistence.
   */
  fun recentFiles(): ModuleSet = moduleSet("recentFiles") {
    module("intellij.platform.recentFiles")
    module("intellij.platform.recentFiles.frontend")
    module("intellij.platform.recentFiles.backend")
  }

  /**
   * Essential platform modules required by most IDE products.
   */
  fun essential(): ModuleSet = moduleSet("essential", includeDependencies = true) {
    // Include minimal essential modules (core backend/frontend, editor, search)
    moduleSet(essentialMinimal())

    // TODO: may be debugger shouldn't be essential? E.g. gateway doesn't need it.
    moduleSet(debugger())

    // The loading="embedded" attribute is required here because the intellij.platform.find module (which is loaded
    // in embedded mode) has a compile dependency on intellij.platform.scopes. Without marking scopes as embedded,
    // this would cause NoClassDefFoundError at runtime when classes from find try to use classes from scopes.
    // This ensures proper classloader hierarchy is maintained for modules that depend on intellij.platform.scopes.
    // This attribute should be removed once the find module no longer needs to be embedded.
    embeddedModule("intellij.platform.scopes")
    module("intellij.platform.scopes.backend")

    // todo navbar is not essential
    module("intellij.platform.navbar")
    module("intellij.platform.navbar.backend")
    module("intellij.platform.navbar.frontend")
    module("intellij.platform.navbar.monolith")
    module("intellij.platform.clouds")

    module("intellij.platform.execution.serviceView")
    module("intellij.platform.execution.serviceView.frontend")
    module("intellij.platform.execution.serviceView.backend")
    module("intellij.platform.execution.dashboard")
    module("intellij.platform.execution.dashboard.frontend")
    module("intellij.platform.execution.dashboard.backend")

    // The loading="embedded" attribute is required here for module synchronization with CWM's ThinClientFindAndReplaceExecutor.
    // Since intellij.platform.frontend.split module loads in embedded mode, and it needs to override the default FindAndReplaceExecutor,
    // the find module must also be marked as embedded to maintain proper dependency loading order.
    // This attribute can be removed once ThinClientFindAndReplaceExecutor is removed.
    embeddedModule("intellij.platform.find")
    module("intellij.platform.find.backend")
    module("intellij.platform.editor.frontend")
    embeddedModule("intellij.platform.managed.cache")
    module("intellij.platform.managed.cache.backend")

    module("intellij.platform.bookmarks.backend")
    module("intellij.platform.bookmarks.frontend")

    moduleSet(recentFiles())

    module("intellij.platform.pluginManager.shared")
    module("intellij.platform.pluginManager.backend")
    module("intellij.platform.pluginManager.frontend")

    module("intellij.platform.execution.impl.frontend")
    module("intellij.platform.execution.impl.backend")
    module("intellij.platform.eel.tcp")

    module("intellij.platform.completion.common")
    module("intellij.platform.completion.frontend")
    module("intellij.platform.completion.backend")

    embeddedModule("intellij.platform.polySymbols")

    // Platform language modules (moved from platformLangBase for consolidation)
    // These provide core IDE functionality needed by all full IDE products
    embeddedModule("intellij.platform.builtInServer.impl")
    embeddedModule("intellij.platform.smRunner")
    embeddedModule("intellij.platform.externalSystem.dependencyUpdater")
    embeddedModule("intellij.platform.externalSystem.impl")
    embeddedModule("intellij.platform.externalProcessAuthHelper")
  }

  /**
   * Provides the platform for implementing Debugger functionality.
   */
  fun debugger(): ModuleSet = moduleSet("debugger", includeDependencies = true) {
    module("intellij.platform.debugger.impl.frontend")
    module("intellij.platform.debugger.impl.backend")
    embeddedModule("intellij.platform.debugger.impl.shared")
    embeddedModule("intellij.platform.debugger.impl.rpc")
    embeddedModule("intellij.platform.debugger.impl.ui")
    embeddedModule("intellij.platform.debugger")
    embeddedModule("intellij.platform.debugger.impl")
  }

  fun fleet(): ModuleSet = moduleSet("fleet", includeDependencies = true) {
    // Same modules as fleet() - all are required
    embeddedModule("fleet.bifurcan")
    embeddedModule("fleet.fastutil")
    embeddedModule("fleet.kernel")
    embeddedModule("fleet.multiplatform.shims")
    embeddedModule("fleet.reporting.api")
    embeddedModule("fleet.reporting.shared")
    embeddedModule("fleet.rhizomedb")
    embeddedModule("fleet.rpc")
    embeddedModule("fleet.util.codepoints")
    embeddedModule("fleet.util.core")
    embeddedModule("fleet.util.logging.api")
    embeddedModule("fleet.util.serialization")
    embeddedModule("fleet.rpc.server")
  }

  /**
   * Minimal RPC infrastructure required by kernel and core platform modules.
   * Contains fleet libraries and base RPC/kernel modules without backend/frontend split.
   *
   * **Use when:** Need minimal RPC support for modules like intellij.platform.kernel
   * without full RPC backend/frontend/topics infrastructure
   *
   * **Note:** Backend modules (`rpc.backend`, `kernel.backend`, `topics.backend`) are in `rpcBackend()`, not here.
   *
   * **Total:** ~15 modules (13 from fleet + 2 platform modules)
   *
   * @see rpcBackend for full RPC functionality with backend/frontend split (includes kernel.backend)
   * @see fleet for the fleet module set definition
   */
  fun rpcMinimal(): ModuleSet = moduleSet("rpc.minimal", outputModule = "intellij.platform.ide.core", includeDependencies = true) {
    // All fleet modules (13 total) including transitive content dependencies
    // All modules are content modules with XML descriptors, so splitting is not practical
    moduleSet(fleet())

    // Base RPC and kernel modules (backend modules are in rpc(), not here)
    embeddedModule("intellij.platform.rpc")
    embeddedModule("intellij.platform.kernel")
  }

  /**
   * Provides RPC backend/frontend split and topics support.
   * 
   * **Assumes base RPC already available:** This module set extends `rpcMinimal()` which is included
   * in `corePlatform()`. It only adds the backend/frontend/topics modules on top of the base.
   * 
   * **Use when:** Building products that need full RPC functionality with backend separation.
   * Products using `essentialMinimal()` get both `rpcMinimal()` (via corePlatform) and this module set.
   * 
   * @see rpcMinimal for base RPC and kernel modules (included in corePlatform)
   */
  fun rpcBackend(): ModuleSet = moduleSet("rpc.backend.extended") {
    // Base RPC (rpcMinimal) already available from corePlatform
    // Only add backend/frontend/topics functionality
    module("intellij.platform.rpc.backend")
    module("intellij.platform.kernel.backend")
    module("intellij.platform.kernel.impl")

    embeddedModule("intellij.platform.rpc.topics")
    module("intellij.platform.rpc.topics.backend")
    module("intellij.platform.rpc.topics.frontend")
  }
}
