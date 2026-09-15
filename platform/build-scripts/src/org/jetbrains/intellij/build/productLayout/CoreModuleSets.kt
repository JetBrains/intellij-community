// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesDap
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesIde
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesOpenTelemetry
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesOpenTelemetryExporter
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesPlatform

/**
 * Core platform module sets forming the foundation of IntelliJ products.
 *
 * This file contains the base module sets that provide the platform infrastructure:
 * - **telemetry***: the platform telemetry API and its implementation, with the OpenTelemetry sets from [LibraryModuleSets]
 * - **corePlatform**: Base platform without IDE (for analysis tools)
 * - **coreIde**: Platform + basic IDE functionality
 * - **coreLang**: Platform + IDE + language support
 * - **fleet**: Fleet kernel and RPC modules
 * - **rpc***: RPC infrastructure
 *
 * Library wrapper sets live in [LibraryModuleSets]; the sets here nest them. CommunityModuleSets builds on top
 * of these with IDE features (essential, debugger, vcs, xml, etc.) and has a one-way dependency on CoreModuleSets.
 *
 * **How to regenerate XML files:**
 * - IDE: Run configuration "Generate Product Layouts"
 * - Bazel: `bazel run //platform/buildScripts:plugin-model-tool`
 */
object CoreModuleSets {
  // region Telemetry

  /**
   * OpenTelemetry API/SDK wrappers and the platform telemetry API.
   *
   * **Typical users:** every product - `intellij.platform.core.impl`, `intellij.platform.analysis.impl`,
   * `intellij.platform.projectModel.impl` and `intellij.platform.workspace.jps` compile against the telemetry
   * API, and so do util-jar modules outside the content model (`intellij.platform.util.io.storages`,
   * `intellij.platform.workspace.storage`, `intellij.platform.jps.model.serialization`).
   *
   * Embedded, and nested in `corePlatform()`: `intellij.platform.diagnostic.telemetry` exposes OpenTelemetry
   * types (`Tracer`, `Meter`) in its own signatures, so the API and its libraries must share the core
   * classloader with it.
   *
   * The exporting half lives in `telemetryImpl()` - nothing in `corePlatform()` needs it.
   *
   * @see telemetryImpl for the OTLP exporters and `TelemetryManager` implementation
   * @see LibraryModuleSets.librariesOpenTelemetry for the nested library set
   */
  fun telemetry(): ModuleSet = moduleSet("telemetry") {
    moduleSet(librariesOpenTelemetry())

    embeddedModule("intellij.platform.diagnostic.telemetry")
  }

  /**
   * The telemetry implementation: `TelemetryManagerImpl`, the OTLP/Jaeger exporters, and the OpenTelemetry
   * exporter libraries only they use.
   *
   * **Typical users:** products that configure telemetry at startup, i.e. everything with
   * `intellij.platform.ide.bootstrap` - hence nesting in `coreLang()` rather than in `corePlatform()`.
   * **Typical NON-users:** CodeServer and other `corePlatform()`-only tools; they need the API, not the export
   * pipeline.
   *
   * `intellij.libraries.opentelemetry.sdk.autoconfigure.spi` and
   * `intellij.libraries.opentelemetry.exporter.sender.jdk` are here because the OTLP exporter needs them at
   * runtime, not because any platform module compiles against them.
   *
   * `intellij.platform.diagnostic.telemetry.exporters` is not listed: the platform layout merges it into
   * `intellij.platform.diagnostic.telemetry.impl.jar`, so it follows `intellij.platform.diagnostic.telemetry.impl` automatically.
   *
   * @see telemetry for the API and the OpenTelemetry API/SDK wrappers
   */
  fun telemetryImpl(): ModuleSet = moduleSet("telemetry.impl", includeDependencies = true) {
    moduleSet(librariesOpenTelemetryExporter())

    embeddedModule("intellij.platform.diagnostic.telemetry.impl")
  }

  // endregion

  // region Platform
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
   * @see [CommunityModuleSets.essentialMinimal] for lightweight IDE with editing (most IDE products should use this)
   */
  fun corePlatform(): ModuleSet = moduleSet("core.platform", selfContained = true, outputModule = "intellij.platform.ide.core", includeDependencies = true) {
    moduleSet(librariesPlatform())
    moduleSet(librariesDap())
    moduleSet(telemetry())

    embeddedModule("intellij.platform.runtime.product")

    module("intellij.platform.buildScripts.concurrency")

    embeddedModule("intellij.platform.util.ex")
    embeddedModule("intellij.platform.util.ui")
    embeddedModule("intellij.platform.util.coroutines")

    embeddedModule("intellij.platform.locking.impl")

    embeddedModule("intellij.platform.core")
    embeddedModule("intellij.platform.core.ui")
    embeddedModule("intellij.platform.core.impl")
    embeddedModule("intellij.platform.indexing")
    // stays here: intellij.codeServer.core needs it directly, and so does the
    // intellij.platform.editor.ex -> intellij.platform.indexing.impl runtime closure
    embeddedModule("intellij.platform.projectFrame")

    embeddedModule("intellij.platform.codeStyle")
    embeddedModule("intellij.platform.editor.ex")
    embeddedModule("intellij.platform.editor.ui")

    embeddedModule("intellij.platform.projectModel")
    embeddedModule("intellij.platform.projectModel.impl")
    embeddedModule("intellij.platform.instanceContainer")
    embeddedModule("intellij.platform.serviceContainer")
    embeddedModule("intellij.platform.workspace.jps")

    // Analysis modules needed by core platform modules
    embeddedModule("intellij.platform.analysis")
    embeddedModule("intellij.platform.analysis.impl")

    moduleSet(rpcMinimal())

    embeddedModule("intellij.platform.ide.core")
    embeddedModule("intellij.platform.ide.core.impl")
    embeddedModule("intellij.platform.ide.core.plugins")
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

    // consumed by intellij.platform.ide - not by anything in corePlatform()
    embeddedModule("intellij.platform.welcomeScreen")

    embeddedModule("intellij.platform.remoteServers.agent.rt")
    embeddedModule("intellij.platform.remoteServers")

    embeddedModule("intellij.platform.usageView")
    embeddedModule("intellij.platform.credentialStore")
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
   * @see [CommunityModuleSets.essentialMinimal] for full minimal IDE (includes this + RPC + editor + search) - RECOMMENDED
   */
  fun coreLang(): ModuleSet = moduleSet("core.lang", includeDependencies = true) {
    // Include core IDE (corePlatform + intellij.platform.ide)
    moduleSet(coreIde())

    // telemetry export pipeline - required by intellij.platform.ide.bootstrap, which configures it at startup
    moduleSet(telemetryImpl())

    embeddedModule("intellij.platform.macro")
    embeddedModule("intellij.platform.usageView.impl")

    // consumed by intellij.platform.ide.impl and intellij.platform.lang.impl
    embeddedModule("intellij.platform.welcomeScreen.impl")
    // consumed by intellij.platform.ide.bootstrap; also PROVIDED-depends on intellij.platform.ide.impl
    embeddedModule("intellij.platform.icons.impl.intellij")

    embeddedModule("intellij.platform.execution")
    embeddedModule("intellij.platform.execution.impl")

    // intellij.platform.lang depends on it
    embeddedModule("intellij.platform.lvcs")

    embeddedModule("intellij.platform.configurationStore.impl")

    embeddedModule("intellij.platform.lang.core")
    embeddedModule("intellij.platform.testIntegration")
    embeddedModule("intellij.platform.testIntegration.ui")
    embeddedModule("intellij.platform.lang")
    embeddedModule("intellij.platform.lang.impl")
    embeddedModule("intellij.platform.syntax.psi")

    embeddedModule("intellij.platform.statistics")
    embeddedModule("intellij.platform.statistics.config")
    embeddedModule("intellij.platform.statistics.uploader")
    embeddedModule("intellij.platform.experiment")
    embeddedModule("intellij.platform.project")
    embeddedModule("intellij.platform.ide.progress")
    embeddedModule("intellij.platform.codeStyle.impl")
    embeddedModule("intellij.platform.refactoring")
    embeddedModule("intellij.platform.ide.impl")
    requiredModule("intellij.platform.ide.util.io.native")
    requiredModule("intellij.platform.ide.osCertificates")
    // keeps marketplace-zip-signer out of the core classloader - loaded only when a plugin signature is verified
    module("intellij.platform.ide.pluginSignatureVerifier")
    // private wrapper used only by intellij.platform.ide.pluginSignatureVerifier, so it is not embedded
    // and is not in librariesPlatform() - products without ide.impl do not need it
    module("intellij.libraries.zip.signer")

    embeddedModule("intellij.platform.rd.community")

    embeddedModule("intellij.platform.remote.core")
    embeddedModule("intellij.platform.ide.remote")
    embeddedModule("intellij.platform.threadDumpParser")
    embeddedModule("intellij.platform.ide.favoritesTreeView")
    // todo not used by platform - move to plugin
    embeddedModule("intellij.platform.ide.designer")

    embeddedModule("intellij.platform.ide.bootstrap")
    embeddedModule("intellij.platform.bootstrap")

    // depends on intellij.platform.ide.impl
    module("intellij.platform.backend.workspace.impl")

    // Additional dependencies specific to lang.impl and ide.impl
    embeddedModule("intellij.platform.ide.concurrency")
    embeddedModule("intellij.platform.builtInServer")
    embeddedModule("intellij.platform.discoverability")
    module("intellij.platform.externalSystem")
    embeddedModule("intellij.platform.eel.impl")
    embeddedModule("intellij.platform.eel.nioFs.impl")
    embeddedModule("intellij.platform.diff")
    embeddedModule("intellij.platform.diff.impl")
    embeddedModule("intellij.platform.util.diff")
    embeddedModule("fleet.andel")
  }

  // endregion

  // region Fleet and RPC

  fun fleet(): ModuleSet = moduleSet("fleet", includeDependencies = true) {
    // Same modules as fleet() - all are required
    embeddedModule("fleet.bifurcan")
    embeddedModule("fleet.fastutil")
    embeddedModule("fleet.kernel")
    embeddedModule("fleet.multiplatform.shims")
    embeddedModule("fleet.openmap")
    embeddedModule("fleet.radixTrie")
    embeddedModule("fleet.reporting.api")
    embeddedModule("fleet.reporting.shared")
    embeddedModule("fleet.rhizomedb")
    embeddedModule("fleet.rhizomedb.transactor")
    embeddedModule("fleet.rhizomedb.transactor.rebase")
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
    embeddedModule("intellij.platform.klogger")
    module("intellij.platform.rpc")
    embeddedModule("intellij.platform.rpc.lite")
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

  // endregion
}
