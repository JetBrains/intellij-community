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
 * - **essential**: IDE editing and navigation features, built from coreLang and the feature sets
 * - **splitCore/credentialStore/editor/searchEverywhere/scopes/find/executionSplit/ideInternal**: the feature sets
 *   that essential nests, and that a lean product adds itself
 * - **debugger**: Debugger platform
 * - **vcs**: Version control support
 * - **xmlRuntime**: the cglib library, for a product that bundles the XML plugin
 * - **externalSystem**: the external system platform, for a product that bundles a build-tool plugin
 * - **composeRuntime**: Compose runtime and Compose Swing, for a product that bundles the Compose plugin
 * - **spellchecker/settingsSync/ml**: one feature with the library it needs
 * - **polySymbols**: the PolySymbols framework, for a product that bundles the XML or the VCS plugin
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
   * Essential platform modules required by most IDE products.
   *
   * The set nests [CoreModuleSets.coreLang] and the feature sets [splitCore], [credentialStore], [editor],
   * [searchEverywhere], [scopes], [find], [executionSplit], [ideInternal] and [builtInServer].
   * A lean product such as Draft takes coreLang and adds only the feature sets that it needs.
   *
   * The direct members are groups that only this set carries today.
   * ADR 0010 (`build/decisions/0010-a-module-set-is-the-leaf-a-fragment-is-the-feature.md`) gives such a group no set.
   * The `completion.*` and `pluginManager.*` groups become sets when a second product takes `essential` without them.
   *
   * The debugger platform is not part of this set. [ideCommon] nests [debugger],
   * and a lean product that needs the debugger adds [debugger] itself.
   */
  fun essential(): ModuleSet = moduleSet("essential") {
    moduleSet(coreLang())
    moduleSet(splitCore())
    moduleSet(credentialStore())
    moduleSet(editor())
    moduleSet(searchEverywhere())
    moduleSet(scopes())
    moduleSet(find())
    moduleSet(executionSplit())
    moduleSet(ideInternal())
    moduleSet(builtInServer())

    embeddedModule("intellij.libraries.download.pgp.verifier")
    embeddedModule("intellij.remoteDev.util")
    embeddedModule("intellij.platform.markdown.utils")

    module("intellij.platform.buildScripts.downloader")
    module("intellij.platform.indexing.impl.backend")
    module("intellij.platform.inline.completion")
    module("intellij.platform.ml")
    module("intellij.platform.managed.cache")
    module("intellij.platform.managed.cache.backend")
    module("intellij.platform.feedback")
    module("intellij.platform.ide.presentationAssistant")
    module("intellij.platform.ide.socketConnection")

    module("intellij.platform.pluginManager.shared.base")
    module("intellij.platform.pluginManager.shared")
    module("intellij.platform.pluginManager.backend")
    module("intellij.platform.pluginManager.frontend")
    module("intellij.platform.ide.updateChecker")

    module("intellij.platform.eel.tcp")

    module("intellij.platform.completion.common")
    module("intellij.platform.completion.frontend")
    module("intellij.platform.completion.backend")
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
    module("intellij.platform.debugger.impl.dashboard")
  }

  // endregion

  // region Feature Module Sets

  /**
   * The built-in HTTP server and its REST services.
   *
   * The API module `intellij.platform.builtInServer` stays in [CoreModuleSets.coreLang], because the core
   * resolves the server through it. The implementation loads in its own class loader.
   *
   * `intellij.platform.externalProcessAuthHelper` is the askpass bridge. The helper app that git, ssh, or sudo
   * starts calls the IDE through a REST endpoint of this server, and the module registers that endpoint.
   * Git4Idea, GitHub, Subversion, Docker SSH, the SSH plugin UI, and the split frontend depend on it.
   */
  fun builtInServer(): ModuleSet = moduleSet("builtInServer") {
    module("intellij.platform.builtInServer.impl")
    module("intellij.platform.externalProcessAuthHelper")
  }

  /**
   * The backend and frontend split anchors of the platform core, and the RPC backend that they need.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun splitCore(): ModuleSet = moduleSet("split.core") {
    moduleSet(rpcBackend())

    module("intellij.platform.settings.local")
    module("intellij.platform.backend")
    module("intellij.platform.project.backend")
    module("intellij.platform.progress.backend")
    module("intellij.platform.lang.impl.backend")
    module("intellij.platform.frontend")
    module("intellij.platform.monolith")
  }

  /**
   * The credential store implementation and its settings UI.
   * The API module `intellij.platform.credentialStore` stays in [CoreModuleSets.coreIde].
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun credentialStore(): ModuleSet = moduleSet("credentialStore") {
    module("intellij.platform.credentialStore.ui")
    module("intellij.platform.credentialStore.impl")
  }

  /**
   * The editor modules and their backend and frontend split.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun editor(): ModuleSet = moduleSet("editor") {
    module("intellij.platform.editor")
    module("intellij.platform.editor.backend")
    module("intellij.platform.editor.frontend")
  }

  /**
   * The Search Everywhere popup and its backend and frontend split.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun searchEverywhere(): ModuleSet = moduleSet("searchEverywhere") {
    module("intellij.platform.searchEverywhere")
    module("intellij.platform.searchEverywhere.backend")
    module("intellij.platform.searchEverywhere.frontend")
  }

  /**
   * The search scopes for the search and find features.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun scopes(): ModuleSet = moduleSet("scopes") {
    module("intellij.platform.scopes")
    module("intellij.platform.scopes.backend")
  }

  /**
   * The Find in Files feature and its backend.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun find(): ModuleSet = moduleSet("find") {
    module("intellij.platform.find")
    module("intellij.platform.find.backend")
  }

  /**
   * The backend and frontend split of the execution implementation.
   * The `intellij.platform.execution.impl` module stays in [CoreModuleSets.coreLang].
   * The `intellij.platform.execution.rpc` module holds the remote topic that syncs the live Run tool window icon, and its publisher.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun executionSplit(): ModuleSet = moduleSet("execution.split") {
    module("intellij.platform.execution.impl.frontend")
    module("intellij.platform.execution.impl.backend")
    module("intellij.platform.execution.rpc")
  }

  /**
   * The internal IDE services and actions, and their backend.
   * The module registers the platform implementations of `StatisticsNotificationManager` and `LatencyRecorder`.
   *
   * [essential] nests this set. A lean product such as Draft adds the set itself.
   */
  fun ideInternal(): ModuleSet = moduleSet("ide.internal") {
    module("intellij.platform.ide.internal")
    module("intellij.platform.ide.internal.backend")
  }

  /**
   * The external system platform: the API, the implementation, and the dependency updater.
   * The build-tool plugins (Gradle, Maven, Amper), the Java and Kotlin plugins, Docker, and the
   * split execution frontend depend on it.
   *
   * [ideCommon] nests this set. A lean product that bundles one of these plugins adds the set itself.
   * `intellij.platform.externalProcessAuthHelper` is not part of this set. It is the askpass bridge,
   * and it lives in [builtInServer].
   */
  fun externalSystem(): ModuleSet = moduleSet("externalSystem") {
    module("intellij.platform.externalSystem")
    module("intellij.platform.externalSystem.impl")
    module("intellij.platform.externalSystem.dependencyUpdater")
  }

  /**
   * VCS (Version Control System) shared anchor modules.
   * Implementation, log, DVCS, and sqlite content is bundled via intellij.platform.vcs.plugin.
   * The microba date picker is a dependency of `intellij.platform.vcs.impl` in that plugin.
   */
  fun vcs(): ModuleSet = moduleSet("vcs") {
    module("intellij.libraries.microba")

    moduleSet(vcsShared())
  }

  /**
   * VCS shared modules (used by both frontend and backend).
   */
  fun vcsShared(): ModuleSet = moduleSet("vcs.shared") {
    module("intellij.platform.vcs.core")
    module("intellij.platform.vcs.shared")
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
   * Duplicates analysis modules.
   */
  fun duplicates(): ModuleSet = moduleSet("duplicates") {
    module("intellij.platform.duplicates.analysis")
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
   * The Compose runtime and Compose Swing modules that stay in the platform.
   * [ideCommon] does not nest this set. A product that bundles the plugin [COMPOSE_PLUGIN_MODULE] adds it,
   * because the plugin content modules depend on `intellij.libraries.compose.runtime.desktop`.
   * The renderer stack with Skiko, Compose Foundation and Jewel is content of that plugin.
   */
  fun composeRuntime(): ModuleSet = moduleSet("compose.runtime") {
    module("intellij.libraries.compose.runtime.desktop")
    module("intellij.libraries.compose.swing")
    module("intellij.platform.compose.swing")
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
    module("intellij.platform.testFramework.junit5.wsl")
  }

  // endregion

  /**
   * RD (Rider and Remote development) common modules.
   * Included in all IDEs
   */
  fun rdCommon(): ModuleSet = moduleSet("rd.common") {
    onDemandModule("intellij.rd.ide.model.generated")
    onDemandModule("intellij.rd.platform")
    onDemandModule("intellij.rd.ui")
    onDemandModule("intellij.platform.split.protocol")

    // These modules are included in all IDEs.
    // They are restricted: only a product or a plugin with [rdClientActivation] can load them.
    // Packaging of those modules to the all IDEs is required to load a JetBrains Client from the big IDE distribution.
    onDemandModule("intellij.rd.client", restricted = true)
    onDemandModule("intellij.rd.client.debugger", restricted = true)
    onDemandModule("intellij.rd.client.base", restricted = true)
    onDemandModule("intellij.rd.client.internal", restricted = true)
  }

  /**
   * The activation of the restricted RD client modules from [rdCommon].
   * JetBrains Client, Rider, and the Radler plugin grant it.
   */
  fun rdClientActivation(): ModuleActivation = ModuleActivation.create(
    required = listOf("intellij.rd.client", "intellij.rd.client.base"),
    allowed = listOf("intellij.rd.client.debugger", "intellij.rd.client.internal", "intellij.rd.client.testFramework"),
  )

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
   * The `intellij.platform.ml` API stays in [essential].
   */
  fun ml(): ModuleSet = moduleSet("ml") {
    module("intellij.platform.ml.impl")
  }

  /**
   * The PolySymbols framework: the API, the backend, and the web-types support.
   * The XML plugin, the VCS plugin (`intellij.platform.vcs.impl` resolves issue links in a commit message),
   * CSS, JavaScript and the web framework plugins depend on it.
   *
   * [ideCommon] nests this set. A lean product that bundles one of these plugins adds the set itself.
   * No module is embedded: no embedded module depends on it, and every consumer declares the dependency.
   */
  fun polySymbols(): ModuleSet = moduleSet("polySymbols") {
    module("intellij.platform.polySymbols")
    module("intellij.platform.polySymbols.backend")
    module("intellij.platform.polySymbols.web")
  }

  /**
   * The XML module that stays in the platform: the cglib library.
   * The XML modules are content of the bundled plugin `intellij.xml.plugin`, or of the Language Server XML Core plugin.
   * cglib is embedded, because `AdvancedEnhancer.getDefaultClassLoader()` defines each generated DOM proxy in the
   * `PluginClassLoader` of one of the proxied interfaces. `net.sf.cglib.proxy.Factory` must resolve from any plugin
   * classloader, and the layout cannot enumerate that set.
   *
   * [ideCommon] nests this set. A lean product that bundles an XML plugin adds the set itself.
   */
  fun xmlRuntime(): ModuleSet = moduleSet("xml.runtime") {
    embeddedModule("intellij.libraries.cglib")
  }

  /**
   * IDE common modules.
   * Nests essential, debugger, spellchecker, settings.sync, ml, externalSystem, polySymbols, vcs, lsp, xml.runtime,
   * duplicates, and the libraries.ide.common and libraries.grpc sets from [LibraryModuleSets].
   * No Compose module is in this set. A product that bundles the plugin [COMPOSE_PLUGIN_MODULE] adds [composeRuntime].
   */
  fun ideCommon(): ModuleSet = moduleSet("ide.common") {
    // Include essential first (which includes coreLang from CoreModuleSets)
    moduleSet(essential())
    // `intellij.platform.scriptDebugger.ui` in this set depends on the debugger modules
    moduleSet(debugger())
    moduleSet(librariesIdeCommon())
    moduleSet(librariesGrpc())
    moduleSet(spellchecker())
    moduleSet(settingsSync())
    moduleSet(ml())
    moduleSet(externalSystem())
    moduleSet(polySymbols())

    // Additional IDE-specific modules
    module("intellij.platform.lvcs.impl")
    module("intellij.platform.collaborationTools")
    module("intellij.platform.collaborationTools.auth")
    module("intellij.platform.collaborationTools.auth.base")
    module("intellij.platform.collaborationTools.shared")
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
    module("intellij.regexp")
    module("intellij.platform.langInjection")
    module("intellij.platform.langInjection.backend")
    module("intellij.platform.versionDownloadManager")

    moduleSet(vcs())
    moduleSet(lsp())
    moduleSet(xmlRuntime())
    moduleSet(duplicates())

    // Note: rd.common is intentionally NOT included in ide.common
    // Reason: Rider uses custom module loading mode due to early backend startup requirements.
    // Products that need rd.common include it explicitly in their product files.
  }

  // endregion
}
