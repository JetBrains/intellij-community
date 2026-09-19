// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreLang
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcBackend
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesGrpc
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesIdeCommon
import org.jetbrains.intellij.build.productLayout.LibraryModuleSets.librariesLsp4j

/**
 * Community module sets for IDE features that build on CoreModuleSets.
 *
 * This file contains IDE feature module sets:
 * - **essentialMinimal/essential**: IDE editing and navigation features
 * - **debugger**: Debugger platform
 * - **vcs**: Version control support
 * - **xml**: XML support
 * - **compose**: Compose UI
 * - **spellchecker/settingsSync/ml**: one feature with the library it needs
 * - **ideCommon**: Full IDE common modules
 *
 * Has a one-way dependency on CoreModuleSets (platform infrastructure, RPC) and LibraryModuleSets (library wrappers).
 *
 * **How to regenerate XML files:**
 * - IDE: Run configuration "Generate Product Layouts"
 * - Bazel: `bazel run //platform/buildScripts:plugin-model-tool`
 *
 * For comprehensive documentation:
 * - [Module Sets](../product-dsl/docs/module-sets.md) - How module sets work and best practices
 *
 * @see CoreModuleSets for platform infrastructure (corePlatform, coreIde, coreLang, rpc, fleet)
 * @see LibraryModuleSets for the library wrapper sets
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
   * - **Gateway**: Remote development gateway - uses `essential()` + `vcsShared()` + the SSH plugin
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
   * @see essential for full IDE with navigation and more features
   * @see CoreModuleSets.coreLang for just language support without editor/search/RPC
   * @see CoreModuleSets.corePlatform for analysis tools without editing
   */
  fun essentialMinimal(): ModuleSet = moduleSet("essential.minimal") {
    // Lang includes corePlatform (which includes librariesPlatform) as nested set
    moduleSet(coreLang())

    embeddedModule("intellij.libraries.download.pgp.verifier")
    embeddedModule("intellij.remoteDev.util")

    // RPC backend functionality (base RPC/kernel already in corePlatform via rpcMinimal)
    moduleSet(rpcBackend())

    module("intellij.platform.buildScripts.downloader")

    embeddedModule("intellij.platform.credentialStore.ui")
    embeddedModule("intellij.platform.credentialStore.impl")

    // Core platform backend/frontend split
    module("intellij.platform.settings.local")
    module("intellij.platform.backend")
    module("intellij.platform.project.backend")
    module("intellij.platform.progress.backend")
    module("intellij.platform.lang.impl.backend")
    module("intellij.platform.indexing.impl.backend")

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

    embeddedModule("intellij.platform.ide.initialConfigImport")
    embeddedModule("intellij.platform.markdown.utils")
    module("intellij.platform.ml")
  }

  /**
   * Essential platform modules required by most IDE products.
   *
   * The debugger platform is not part of this set. [ideCommon] nests [debugger],
   * and a lean product that needs the debugger adds [debugger] itself.
   */
  fun essential(): ModuleSet = moduleSet("essential") {
    // Include minimal essential modules (core backend/frontend, editor, search)
    moduleSet(essentialMinimal())

    module("intellij.platform.scopes")
    module("intellij.platform.scopes.backend")

    module("intellij.platform.find")
    module("intellij.platform.find.backend")
    module("intellij.platform.editor.frontend")
    module("intellij.platform.managed.cache")
    module("intellij.platform.managed.cache.backend")
    module("intellij.platform.ide.internal")
    module("intellij.platform.ide.internal.backend")
    embeddedModule("intellij.platform.feedback")

    module("intellij.platform.pluginManager.shared.base")
    module("intellij.platform.pluginManager.shared")
    module("intellij.platform.pluginManager.backend")
    module("intellij.platform.pluginManager.frontend")
    embeddedModule("intellij.platform.ide.updateChecker")
    module("intellij.platform.ide.updateChecker.backend")

    module("intellij.platform.execution.impl.frontend")
    module("intellij.platform.execution.impl.backend")
    module("intellij.platform.eel.tcp")

    module("intellij.platform.completion.common")
    module("intellij.platform.completion.frontend")
    module("intellij.platform.completion.backend")

    embeddedModule("intellij.platform.polySymbols")
    module("intellij.platform.polySymbols.web")

    // Platform language modules (moved from platformLangBase for consolidation)
    // These provide core IDE functionality needed by all full IDE products
    embeddedModule("intellij.platform.builtInServer.impl")
    module("intellij.platform.externalSystem.dependencyUpdater")
    module("intellij.platform.externalSystem.impl")
    module("intellij.platform.externalProcessAuthHelper")

    module("intellij.platform.util.commonsLangV2Shim")
  }

  /**
   * Provides the platform for implementing Debugger functionality.
   *
   * [ideCommon] nests this set. A lean product that needs the debugger adds this set itself.
   * Gateway does not need it.
   */
  fun debugger(): ModuleSet = moduleSet("debugger") {
    module("intellij.platform.debugger.impl.frontend")
    module("intellij.platform.debugger.impl.backend")
    module("intellij.platform.debugger.impl.shared")
    module("intellij.platform.debugger.impl.rpc")
    module("intellij.platform.debugger.impl.ui")
    module("intellij.platform.debugger")
    module("intellij.platform.debugger.impl")
  }

  // endregion

  // region Feature Module Sets

  /**
   * VCS (Version Control System) shared anchor modules.
   * Implementation, log, DVCS, and sqlite content is bundled via intellij.platform.vcs.plugin.
   * The microba date picker is a dependency of `intellij.platform.vcs.impl` in that plugin.
   */
  fun vcs(): ModuleSet = moduleSet("vcs") {
    module("intellij.platform.vcs")
    module("intellij.libraries.microba")

    moduleSet(vcsShared())
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
   * Language Server Protocol (LSP) support modules.
   */
  fun lsp(): ModuleSet = moduleSet("lsp") {
    moduleSet(librariesLsp4j())
    embeddedModule("intellij.platform.lsp")
    embeddedModule("intellij.platform.lsp.impl")
    module("intellij.platform.lsp.impl.structureView")
  }

  /**
   * JSP base API modules — shared JSP language base used by Java, Kotlin, Lombok plugins and language servers.
   * Kept in its own module set because it does not belong to `essential` (JSP-specific) and needs its own
   * classloader to depend on xml.psi (a separate content module).
   */
  fun jspBase(): ModuleSet = moduleSet("jsp.base") {
    module("intellij.jsp.base")
  }

  /**
   * XML support modules without Structure View UI.
   * The other products bundle the `intellij.xml.plugin` wrapper plugin instead.
   */
  fun xmlWithoutStructureView(): ModuleSet = moduleSet("xml.without.structureView", alias = "com.intellij.modules.xml") {
    module("intellij.xml.dom")
    module("intellij.xml.dom.impl")
    module("intellij.xml.psi")
    module("intellij.xml.psi.impl")
    module("intellij.xml.analysis")
    module("intellij.xml.emmet")
    module("intellij.xml.emmet.shared")
    module("intellij.xml.emmet.backend")
    module("intellij.xml.emmet.frontend")
    module("intellij.xml.ui.common")
    module("intellij.xml.parser")
    module("intellij.xml.syntax")
    module("intellij.relaxng")
    // kept embedded (i.e. loaded by the core classloader): `AdvancedEnhancer.getDefaultClassLoader()` defines each
    // generated DOM proxy in the `PluginClassLoader` of one of the proxied interfaces, so `net.sf.cglib.proxy.Factory`
    // has to be resolvable from any plugin classloader - a set the layout cannot enumerate.
    embeddedModule("intellij.libraries.cglib")
    module("intellij.libraries.isorelax")
    module("intellij.libraries.jing")
    module("intellij.libraries.xerces")
    module("intellij.libraries.xml.resolver")
    module("intellij.xml.impl")
    module("intellij.xml.analysis.impl")
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
   * The platform resources a product can leave out: the default file types, the platform application-info fixtures,
   * and the default file templates.
   *
   * [CoreModuleSets.coreIde] does not nest this set. CLion, WebStorm and AppCode ship no default file types, GoLand
   * ships no platform application-info fixtures, and DataGrip ships none of the three. A product that ships a subset
   * names the modules it ships. Every other product adds this set.
   *
   * The modules are embedded: `FileTypeManagerImpl` and `ApplicationNamesInfo` read the resources through the core
   * classloader, and `FileTemplatesLoader` scans the core classpath for `fileTemplates/`.
   */
  fun platformResourceDefaults(): ModuleSet = moduleSet("platform.resources.defaults") {
    embeddedModule("intellij.platform.resources.fileTypes")
    embeddedModule("intellij.platform.resources.applicationInfo")
    embeddedModule("intellij.platform.resources.en.fileTemplates")
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
   * `intellij.libraries.compose.runtime.desktop` depends on the jspecify annotations.
   */
  fun compose(): ModuleSet = moduleSet("compose") {
    module("intellij.libraries.jspecify")
    module("intellij.libraries.skiko")
    module("intellij.libraries.coil")
    module("intellij.libraries.compose.swing")
    module("intellij.platform.compose")
    module("intellij.platform.compose.markdown")
    module("intellij.platform.compose.swing")
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
    module("intellij.platform.jewel.markdown.extensions.frontMatter")
    module("intellij.platform.jewel.markdown.extensions.images")
    module("intellij.platform.jewel.markdown.core")
  }

  /**
   * Core platform test framework modules.
   * These are commonly needed by test plugins and are duplicated across products.
   */
  fun platformTestFrameworksCore(): ModuleSet = moduleSet("platform.testFrameworks.core") {
    module("intellij.libraries.jetcheck")
    module("intellij.libraries.kaml")
    module("intellij.libraries.memoryfilesystem")
    module("intellij.platform.testExtensions", allowedMissingPluginIds = listOf("org.jetbrains.ls.plugin.java"))
    module("intellij.platform.testFramework", allowedMissingPluginIds = listOf("com.intellij.java", "com.intellij.platform.images"))
    module("intellij.platform.testFramework.common")
    module("intellij.platform.testFramework.core")
    module("intellij.platform.testFramework.impl")
    module("intellij.platform.testFramework.teamCity")
    module("intellij.codeowners")
    module("intellij.codeowners.monorepo.resolver")
    module("intellij.codeowners.runtime.resolver")
  }

  /**
   * JUnit 5 test framework modules for test plugins.
   * Includes the base JUnit 5 integration plus project structure, EEL, and WSL support.
   */
  fun platformTestFrameworksJunit5(): ModuleSet = moduleSet("platform.testFrameworks.junit5") {
    module("intellij.platform.testFramework.junit5")
    module("intellij.platform.testFramework.junit5.projectStructure")
    module("intellij.platform.testFramework.junit5.codeInsight")
    module("intellij.platform.testFramework.junit5.tests")
    module("intellij.platform.testFramework.junit5.eel.tests")
    module("intellij.platform.testFramework.junit5.wsl._test")
  }

  // endregion

  /**
   * RD (Rider and Remote development) common modules.
   * Included in all IDEs
   */
  fun rdCommon(): ModuleSet = moduleSet("rd.common") {
    module("intellij.rd.ide.model.generated")
    module("intellij.rd.platform")
    module("intellij.rd.ui")
    module("intellij.platform.split.protocol")

    // These modules are included in all IDEs.
    // However, they are due to intellij.rd.client -> intellij.rd.client.base -> com.intellij.rd.client.capable alias,
    // Those modules are loaded only: in JetBrains Client, Rider and an IDE if a Radler is installed.
    // Packaging of those modules to the all IDEs is required to load a JetBrains Client from the big IDE distribution.
    module("intellij.rd.client")
    module("intellij.rd.client.debugger")
    module("intellij.rd.client.base")
    module("intellij.rd.client.internal")
  }

  /**
   * The spellchecker core module and its Lucene dictionary index.
   * The VCS and XML spellchecker strategies stay in [ideCommon], because lean products bundle the core without them.
   */
  fun spellchecker(): ModuleSet = moduleSet("spellchecker") {
    module("intellij.spellchecker")
    module("intellij.libraries.lucene.common")
  }

  /**
   * Settings Sync core and the JGit library it stores settings with.
   */
  fun settingsSync(): ModuleSet = moduleSet("settings.sync") {
    module("intellij.settingsSync.core")
    module("intellij.libraries.jgit")
  }

  /**
   * ML platform implementation, consumed only by the ML ranking plugins.
   * The `intellij.platform.ml` API stays embedded in [essentialMinimal].
   */
  fun ml(): ModuleSet = moduleSet("ml") {
    module("intellij.platform.ml.impl")
  }

  /**
   * IDE common modules.
   * Nests essential, debugger, compose, spellchecker, settings.sync, ml, vcs, lsp, duplicates, and the
   * libraries.ide.common and libraries.grpc sets from [LibraryModuleSets].
   */
  fun ideCommon(): ModuleSet = ideCommon(includeCompose = true)

  /**
   * [ideCommon] without the nested [compose] set (skiko, Compose, Jewel, coil).
   *
   * **Use when:** the product renders no Compose UI.
   * The compose set is about 80 MB of jars plus the skiko native runtime.
   *
   * **Example products:**
   * - **JetBrains Light**
   */
  fun ideCommonWithoutCompose(): ModuleSet = ideCommon(includeCompose = false)

  /**
   * The generator discovers a module set through a public function without parameters,
   * so each variant has its own public entry point above.
   */
  private fun ideCommon(includeCompose: Boolean): ModuleSet = moduleSet(if (includeCompose) "ide.common" else "ide.common.without.compose") {
    // Include essential first (which includes coreLang from CoreModuleSets)
    moduleSet(essential())
    if (includeCompose) {
      moduleSet(compose())
    }
    // `intellij.platform.scriptDebugger.ui` in this set depends on the debugger modules
    moduleSet(debugger())
    moduleSet(librariesIdeCommon())
    moduleSet(librariesGrpc())
    moduleSet(spellchecker())
    moduleSet(settingsSync())
    moduleSet(ml())

    // Additional IDE-specific modules
    module("intellij.platform.lvcs.impl")
    module("intellij.platform.collaborationTools")
    module("intellij.platform.collaborationTools.auth")
    module("intellij.platform.collaborationTools.auth.base")
    module("intellij.platform.scriptDebugger.ui")
    module("intellij.platform.scriptDebugger.backend")
    module("intellij.platform.scriptDebugger.protocolReaderRuntime")

    module("intellij.platform.diagnostic.freezeAnalyzer")
    module("intellij.platform.warmup")
    module("intellij.platform.inspect")
    module("intellij.spellchecker.vcs")
    module("intellij.spellchecker.xml")
    module("intellij.platform.buildView")
    module("intellij.platform.buildView.backend")
    module("intellij.platform.buildView.frontend")
    module("intellij.platform.projectView")
    module("intellij.platform.projectView.backend")
    module("intellij.platform.projectView.frontend")
    module("intellij.emojipicker")
    module("intellij.platform.ide.impl.wsl")
    module("intellij.platform.diagnostic.telemetry.agent.extension")
    // todo: move to essential modules when not embedded
    module("intellij.platform.polySymbols.backend")
    module("intellij.regexp")
    module("intellij.platform.langInjection")
    module("intellij.platform.langInjection.backend")

    moduleSet(vcs())
    moduleSet(lsp())
    // the other xml modules live in the `intellij.xml.plugin` wrapper plugin; cglib is kept embedded
    // (i.e. loaded by the core classloader): `AdvancedEnhancer.getDefaultClassLoader()` defines each
    // generated DOM proxy in the `PluginClassLoader` of one of the proxied interfaces, so `net.sf.cglib.proxy.Factory`
    // has to be resolvable from any plugin classloader - a set the layout cannot enumerate.
    embeddedModule("intellij.libraries.cglib")
    moduleSet(duplicates())

    // Note: rd.common is intentionally NOT included in ide.common
    // Reason: Rider uses custom module loading mode due to early backend startup requirements.
    // Products that need rd.common include it explicitly in their product files.
  }

  // endregion
}
