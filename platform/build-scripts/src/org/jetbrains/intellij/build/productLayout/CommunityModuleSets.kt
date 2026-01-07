// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.essential
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreLang
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.librariesKtor
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.librariesMisc
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcBackend

/**
 * Community module sets for IDE features that build on CoreModuleSets.
 *
 * This file contains IDE feature module sets:
 * - **essentialMinimal/essential**: IDE editing and navigation features
 * - **debugger**: Debugger platform
 * - **vcs**: Version control support
 * - **xml**: XML support
 * - **compose**: Compose UI
 * - **ideCommon**: Full IDE common modules
 *
 * Has a one-way dependency on CoreModuleSets (libraries, platform infrastructure, RPC).
 *
 * **How to regenerate XML files:**
 * - IDE: Run configuration "Generate Product Layouts"
 * - Bazel: `bazel run //platform/buildScripts:plugin-model-tool`
 *
 * @see CoreModuleSets for platform infrastructure (libraries, corePlatform, coreIde, coreLang, rpc, fleet)
 * @see <a href="../product-dsl/module-sets.md">Module Sets Documentation</a>
 */
object CommunityModuleSets {
  // region Essential and Debugger

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
   * @see CoreModuleSets.coreLang for just language support without editor/search/RPC
   * @see CoreModuleSets.corePlatform for analysis tools without editing
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

    module("intellij.platform.todo")
    module("intellij.platform.todo.backend")

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

    module("intellij.java.aetherDependencyResolver")
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

  // endregion

  // region Feature Module Sets

  /**
   * VCS (Version Control System) modules including shared and frontend parts.
   */
  fun vcs(): ModuleSet = moduleSet("vcs") {
    module("intellij.platform.vcs.impl")
    module("intellij.platform.vcs.impl.exec")
    module("intellij.platform.vcs.impl.lang")
    module("intellij.platform.vcs.impl.lang.actions")
    module("intellij.platform.vcs.log")
    module("intellij.platform.vcs.log.impl")
    module("intellij.platform.vcs.log.graph")
    module("intellij.platform.vcs.log.graph.impl")
    module("intellij.platform.vcs.dvcs")
    module("intellij.platform.vcs.dvcs.impl")
    embeddedModule("intellij.platform.vcs")

    moduleSet(vcsShared())
    moduleSet(vcsFrontend())
  }

  /**
   * VCS shared modules (used by both frontend and backend).
   */
  fun vcsShared(): ModuleSet = moduleSet("vcs.shared") {
    embeddedModule("intellij.platform.vcs.core")
    embeddedModule("intellij.platform.vcs.shared")
    module("intellij.platform.vcs.impl.shared")
    module("intellij.platform.vcs.dvcs.impl.shared")
  }

  /**
   * VCS frontend modules.
   */
  fun vcsFrontend(): ModuleSet = moduleSet("vcs.frontend") {
    module("intellij.platform.vcs.impl.frontend")
  }

  /**
   * XML support modules.
   */
  fun xml(): ModuleSet = moduleSet("xml", alias = "com.intellij.modules.xml") {
    embeddedModule("intellij.xml.dom")
    embeddedModule("intellij.xml.dom.impl")
    embeddedModule("intellij.xml.structureView")
    embeddedModule("intellij.xml.structureView.impl")
    embeddedModule("intellij.xml.psi")
    embeddedModule("intellij.xml.psi.impl")
    embeddedModule("intellij.xml.analysis")
    module("intellij.xml.emmet")
    module("intellij.xml.emmet.backend")
    embeddedModule("intellij.xml.ui.common")
    embeddedModule("intellij.xml.parser")
    embeddedModule("intellij.xml.syntax")
    module("intellij.relaxng")
    embeddedModule("intellij.xml.impl")
    embeddedModule("intellij.xml.analysis.impl")
    // embedded because intellij.xml.dom.impl which depends on it, is also embedded
    embeddedModule("intellij.libraries.cglib")
    embeddedModule("intellij.libraries.xerces")
    module("intellij.xml.langInjection")
    module("intellij.xml.langInjection.xpath")
  }

  /**
   * Duplicates analysis modules.
   */
  fun duplicates(): ModuleSet = moduleSet("duplicates") {
    embeddedModule("intellij.platform.duplicates.analysis")
  }

  /**
   * Stream debugger modules.
   */
  fun debuggerStreams(): ModuleSet = moduleSet("debugger.streams") {
    module("intellij.debugger.streams.core")
    module("intellij.debugger.streams.shared")
    module("intellij.debugger.streams.backend")
  }

  /**
   * Process elevation support (for operations requiring elevated privileges).
   */
  fun elevation(): ModuleSet = moduleSet("elevation") {
    module("intellij.execution.process.elevation")
    module("intellij.execution.process.mediator.client")
    module("intellij.execution.process.mediator.common")
    module("intellij.execution.process.mediator.daemon")
  }

  /**
   * Compose UI modules.
   */
  fun compose(): ModuleSet = moduleSet("compose") {
    module("intellij.libraries.skiko")
    module("intellij.libraries.coil")
    module("intellij.platform.compose")
    module("intellij.platform.compose.markdown")
    module("intellij.platform.jewel.foundation")
    module("intellij.libraries.compose.foundation.desktop")
    module("intellij.libraries.compose.runtime.desktop")
    module("intellij.platform.jewel.ui")
    module("intellij.platform.jewel.ideLafBridge")
    module("intellij.platform.jewel.markdown.ideLafBridgeStyling")
    module("intellij.platform.jewel.markdown.extensions.autolink")
    module("intellij.platform.jewel.markdown.extensions.gfmAlerts")
    module("intellij.platform.jewel.markdown.extensions.gfmTables")
    module("intellij.platform.jewel.markdown.extensions.gfmStrikethrough")
    module("intellij.platform.jewel.markdown.extensions.images")
    module("intellij.platform.jewel.markdown.core")
  }

  /**
   * Grid/data viewer core modules.
   */
  fun gridCore(): ModuleSet = moduleSet("grid.core") {
    module("intellij.grid")
    module("intellij.grid.types")
    module("intellij.grid.csv.core.impl")
    module("intellij.grid.core.impl")
    module("intellij.grid.impl")
    module("intellij.grid.impl.ide")
  }

  /**
   * Test framework libraries (JUnit 4, JUnit 5, Hamcrest).
   * Standalone module set for test dependencies.
   */
  @Suppress("unused")
  fun librariesTestFrameworks(): ModuleSet = moduleSet("libraries.testFrameworks") {
    module("intellij.libraries.assertj.core")

    module("intellij.libraries.hamcrest")

    module("intellij.libraries.junit4")
    module("intellij.libraries.kotlinTest")

    module("intellij.libraries.junit5")
    module("intellij.libraries.junit5.jupiter")
    module("intellij.libraries.junit5.launcher")
    module("intellij.libraries.junit5.vintage")
  }

  /**
   * Platform test framework modules (Java test framework, JUnit 5 support, IDE starter).
   * Standalone module set for platform test dependencies.
   */
  @Suppress("unused")
  fun platformTestFrameworks(): ModuleSet = moduleSet("platform.testFrameworks") {
    module("intellij.java.testFramework")
    module("intellij.java.testFramework.backend")
    module("intellij.java.testFramework.shared")
    module("intellij.platform.testFramework.junit5")
    module("intellij.platform.testFramework.junit5.codeInsight")
    module("intellij.tools.ide.starter")
    module("intellij.tools.ide.util.common")
  }

  /**
   * Remote development common modules.
   */
  fun rdCommon(): ModuleSet = moduleSet("rd.common") {
    module("intellij.rd.ide.model.generated")
    module("intellij.rd.platform")
    module("intellij.rd.ui")
  }

  /**
   * IDE common modules (includes essential, compose, grid.core, vcs, xml, duplicates).
   */
  fun ideCommon(): ModuleSet = moduleSet("ide.common") {
    // Include essential first (which includes coreLang from CoreModuleSets)
    moduleSet(essential())
    moduleSet(compose())

    // Additional IDE-specific modules
    module("intellij.platform.lvcs.impl")
    module("intellij.platform.smRunner.vcs")
    module("intellij.platform.collaborationTools")
    module("intellij.platform.collaborationTools.auth")
    module("intellij.platform.collaborationTools.auth.base")
    module("intellij.platform.tasks")
    module("intellij.platform.tasks.impl")
    module("intellij.platform.scriptDebugger.ui")
    module("intellij.platform.scriptDebugger.backend")
    module("intellij.platform.scriptDebugger.protocolReaderRuntime")
    module("intellij.platform.ml.impl")
    module("intellij.libraries.microba")
    module("intellij.platform.diagnostic.freezeAnalyzer")
    module("intellij.platform.diagnostic.freezes")
    module("intellij.platform.warmup")
    module("intellij.platform.inspect")
    module("intellij.settingsSync.core")
    module("intellij.spellchecker")
    module("intellij.spellchecker.xml")
    module("intellij.platform.buildView")
    module("intellij.platform.buildView.backend")
    module("intellij.platform.buildView.frontend")
    module("intellij.emojipicker")
    module("intellij.platform.ide.impl.wsl")
    module("intellij.platform.diagnostic.telemetry.agent.extension")
    // todo: move to essential modules when not embedded
    embeddedModule("intellij.platform.polySymbols.backend")
    embeddedModule("intellij.regexp")
    module("intellij.platform.langInjection")
    module("intellij.platform.langInjection.backend")
    module("intellij.libraries.grpc")
    module("intellij.libraries.grpc.netty.shaded")

    moduleSet(gridCore())
    moduleSet(vcs())
    moduleSet(xml())
    moduleSet(duplicates())
    module("intellij.platform.structuralSearch")

    // Note: rd.common is intentionally NOT included in ide.common
    // Reason: Rider uses custom module loading mode due to early backend startup requirements.
    // Products that need rd.common include it explicitly in their product files.
  }

  // endregion
}