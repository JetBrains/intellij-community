// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

/**
 * Core platform module sets forming the foundation of IntelliJ products.
 *
 * This file contains the base module sets that provide the platform infrastructure:
 * - **libraries***: Library modules (platform, IDE, ktor, Jackson)
 * - **corePlatform**: Base platform without IDE (for analysis tools)
 * - **coreIde**: Platform + basic IDE functionality
 * - **coreLang**: Platform + IDE + language support
 * - **fleet**: Fleet kernel and RPC modules
 * - **rpc***: RPC infrastructure
 *
 * CommunityModuleSets builds on top of these with IDE features (essential, debugger, vcs, xml, etc.)
 * and has a one-way dependency on CoreModuleSets.
 *
 * **How to regenerate XML files:**
 * - IDE: Run configuration "Generate Product Layouts"
 * - Bazel: `bazel run //platform/buildScripts:plugin-model-tool`
 */
object CoreModuleSets {
  // region Libraries

  /**
   * Core platform library modules required by ALL products including analysis tools.
   * Contains universal utilities: serialization, compression, collections, parsing, networking.
   *
   * **Typical users:** All products (CodeServer, IDEA, PyCharm, etc.)
   *
   * **Note:** UI/IDE-specific libraries (JCEF, Jediterm, PTY4J, SSH) have been moved to `librariesIde()`
   *
   * @see librariesIde for UI and IDE-specific libraries
   */
  fun librariesPlatform(): ModuleSet = moduleSet("libraries.platform") {
    embeddedModule("intellij.libraries.java.compatibility")

    embeddedModule("intellij.libraries.kotlin.reflect")
    // intellij.platform.wsl.impl and intellij.platform.util.http uses it
    embeddedModule("intellij.libraries.kotlinx.io")

    // todo - JB Client should not embed intellij.platform.split
    embeddedModule("intellij.libraries.kotlinx.serialization.cbor")

    embeddedModule("intellij.libraries.kotlinx.serialization.core")
    embeddedModule("intellij.libraries.kotlinx.serialization.json")
    embeddedModule("intellij.libraries.kotlinx.serialization.protobuf")
    embeddedModule("intellij.libraries.kotlinx.collections.immutable")
    embeddedModule("intellij.libraries.kotlinx.datetime")
    embeddedModule("intellij.libraries.kotlinx.html")
    // kotlinx-coroutines libraries
    embeddedModule("intellij.libraries.kotlinx.coroutines.core")
    embeddedModule("intellij.libraries.kotlinx.coroutines.debug")
    module("intellij.libraries.kotlinx.coroutines.guava")
    @Suppress("GrazieInspection")
    // Space plugin uses it and bundles into IntelliJ IDEA, but not bundles into DataGrip, so, or Space plugin should bundle this lib,
    // or IJ Platform. As it is a small library and consistency is important across other coroutine libs, bundle to IJ Platform.
    // note 2: despite what we use as "used by", AIA tests broken —
    //   com.intellij.ml.llm.end2end.tests.agent.AiAgentSmokeTest.No error in AI agents communication
    //     java.lang.NoClassDefFoundError: kotlinx/coroutines/slf4j/MDCContext
    //      at io.ktor.client.plugins.observer.ResponseObserverContextJvmKt.getResponseObserverContext(ResponseObserverContextJvm.kt:11)
    // so, we embed it
    embeddedModule("intellij.libraries.kotlinx.coroutines.slf4j")
    embeddedModule("intellij.libraries.aalto.xml")
    embeddedModule("intellij.libraries.asm")
    embeddedModule("intellij.libraries.asm.tools")
    embeddedModule("intellij.libraries.automaton")
    embeddedModule("intellij.libraries.bouncy.castle.provider")
    embeddedModule("intellij.libraries.bouncy.castle.pgp")
    embeddedModule("intellij.libraries.blockmap")
    embeddedModule("intellij.libraries.caffeine")
    embeddedModule("intellij.libraries.classgraph")
    embeddedModule("intellij.libraries.cli.parser")
    embeddedModule("intellij.libraries.commons.cli")
    embeddedModule("intellij.libraries.commons.codec")
    embeddedModule("intellij.libraries.commons.compress")
    embeddedModule("intellij.libraries.commons.io")
    embeddedModule("intellij.libraries.commons.imaging")
    embeddedModule("intellij.libraries.commons.lang3")
    embeddedModule("intellij.libraries.commons.logging")
    embeddedModule("intellij.libraries.fastutil")
    embeddedModule("intellij.libraries.gson")
    embeddedModule("intellij.libraries.guava")
    embeddedModule("intellij.libraries.hash4j")
    embeddedModule("intellij.libraries.hdr.histogram")
    embeddedModule("intellij.libraries.http.client")
    embeddedModule("intellij.libraries.icu4j")
    embeddedModule("intellij.libraries.imgscalr")
    embeddedModule("intellij.libraries.ini4j")
    embeddedModule("intellij.libraries.ion")

    moduleSet(librariesJackson2())
    moduleSet(librariesJackson3())

    embeddedModule("intellij.libraries.java.websocket")
    embeddedModule("intellij.libraries.javax.annotation")
    // used by intellij.platform.util.jdom, so, embedded
    embeddedModule("intellij.libraries.jaxen")
    embeddedModule("intellij.libraries.jbr")
    embeddedModule("intellij.libraries.jcip")
    embeddedModule("intellij.libraries.jsoup")
    embeddedModule("intellij.libraries.jsonpath")
    embeddedModule("intellij.libraries.jsvg")
    embeddedModule("intellij.libraries.jvm.native.trusted.roots")
    embeddedModule("intellij.libraries.jzlib")
    embeddedModule("intellij.libraries.kryo5")
    embeddedModule("intellij.libraries.lz4")
    embeddedModule("intellij.libraries.markdown")
    embeddedModule("intellij.libraries.mvstore")

    embeddedModule("intellij.libraries.netty.buffer")
    embeddedModule("intellij.libraries.netty.codec.compression")
    embeddedModule("intellij.libraries.netty.codec.http")
    embeddedModule("intellij.libraries.netty.handler.proxy")

    embeddedModule("intellij.libraries.oro.matcher")
    embeddedModule("intellij.libraries.protobuf")
    embeddedModule("intellij.libraries.proxy.vole")
    embeddedModule("intellij.libraries.rhino")
    embeddedModule("intellij.libraries.semver")
    embeddedModule("intellij.libraries.snakeyaml")
    embeddedModule("intellij.libraries.snakeyaml.engine")
    embeddedModule("intellij.libraries.stream")
    embeddedModule("intellij.libraries.velocity")
    embeddedModule("intellij.libraries.xtext.xbase")
    embeddedModule("intellij.libraries.xz")

    embeddedModule("intellij.libraries.opentelemetry")
    embeddedModule("intellij.libraries.opentelemetry.extension.kotlin")
    embeddedModule("intellij.libraries.opentelemetry.exporter.otlp.common")
    embeddedModule("intellij.libraries.opentelemetry.semconv")
    embeddedModule("intellij.libraries.opentelemetry.sdk.autoconfigure.spi")
    embeddedModule("intellij.libraries.opentelemetry.exporter.sender.jdk")
  }

  /**
   * Jackson 2 library wrapper modules.
   *
   * Kept as a dedicated module set so that `librariesPlatform()` stays focused on truly universal utilities.
   *
   * Included transitively by `librariesPlatform()`.
   */
  fun librariesJackson2(): ModuleSet = moduleSet("libraries.jackson2") {
    embeddedModule("intellij.libraries.jackson.annotations")
    embeddedModule("intellij.libraries.jackson")
    embeddedModule("intellij.libraries.jackson.jr.objects")
    embeddedModule("intellij.libraries.jackson.databind")

    module("intellij.libraries.jackson.dataformat.xml")
    embeddedModule("intellij.libraries.jackson.dataformat.yaml")
    module("intellij.libraries.jackson.dataformat.toml")

    module("intellij.libraries.jackson.datatype.jdk8")
    module("intellij.libraries.jackson.datatype.jsr310")

    embeddedModule("intellij.libraries.jackson.module.kotlin")
  }

  /**
   * Jackson 3 library wrapper modules.
   *
   * Kept as a dedicated module set so that `librariesPlatform()` stays focused on truly universal utilities.
   *
   * Included transitively by `librariesPlatform()`.
   */
  fun librariesJackson3(): ModuleSet = moduleSet("libraries.jackson3") {
    embeddedModule("intellij.libraries.jackson3")
    embeddedModule("intellij.libraries.jackson3.jr.objects")
    embeddedModule("intellij.libraries.jackson3.databind")
    embeddedModule("intellij.libraries.jackson3.dataformat.yaml")
    embeddedModule("intellij.libraries.jackson3.module.kotlin")
  }

  /**
   * UI and IDE-specific library modules.
   * Contains libraries for browser embedding, terminal UI, SSH, and other IDE features.
   *
   * **Typical use cases:** Full IDEs with user interface (IDEA, PyCharm, WebStorm, etc.)
   * **Typical NON-users:** CodeServer (analysis-only tool), headless tools, pure analysis products
   *
   * **Note:** Image libraries (imgscalr, jsvg) are in `librariesPlatform()` as they're needed by `platform.util.ui`
   */
  fun librariesIde(): ModuleSet = moduleSet("libraries.ide") {
    embeddedModule("intellij.libraries.jediterm.core")
    embeddedModule("intellij.libraries.jediterm.ui")
    embeddedModule("intellij.libraries.jgoodies.common")
    embeddedModule("intellij.libraries.jgoodies.forms")
    embeddedModule("intellij.libraries.jsch.agent.proxy")
    embeddedModule("intellij.libraries.miglayout.swing")
    embeddedModule("intellij.libraries.pty4j")
    embeddedModule("intellij.libraries.sshj")
    embeddedModule("intellij.libraries.swingx")
    embeddedModule("intellij.libraries.winp")

    embeddedModule("intellij.libraries.rd.core")
    embeddedModule("intellij.libraries.rd.framework")
    embeddedModule("intellij.libraries.rd.swing")
    embeddedModule("intellij.libraries.rd.text")
  }

  /**
   * Ktor library modules for HTTP client communication.
   *
   * **Typical use cases:** RPC infrastructure, Remote Dev, Fleet backend, HTTP-based integrations
   * **Typical NON-users:** CodeServer (analysis-only, no RPC), minimal IDEs without remote features
   */
  fun librariesKtor(): ModuleSet = moduleSet("libraries.ktor") {
    embeddedModule("intellij.libraries.ktor.io")
    embeddedModule("intellij.libraries.ktor.utils")
    embeddedModule("intellij.libraries.ktor.network.tls")
    embeddedModule("intellij.libraries.ktor.server.cio")
    embeddedModule("intellij.libraries.ktor.client")
    embeddedModule("intellij.libraries.ktor.client.cio")
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

    embeddedModule("intellij.platform.runtime.product")

    embeddedModule("intellij.platform.diagnostic.telemetry")

    embeddedModule("intellij.platform.util.ex")
    embeddedModule("intellij.platform.util.ui")
    embeddedModule("intellij.platform.util.coroutines")

    embeddedModule("intellij.platform.icons.impl.intellij")

    embeddedModule("intellij.platform.locking.impl")

    embeddedModule("intellij.platform.core")
    embeddedModule("intellij.platform.core.ui")
    embeddedModule("intellij.platform.core.impl")
    embeddedModule("intellij.platform.indexing")
    embeddedModule("intellij.platform.projectFrame")
    embeddedModule("intellij.platform.welcomeScreen")
    embeddedModule("intellij.platform.welcomeScreen.impl")

    embeddedModule("intellij.platform.codeStyle")
    embeddedModule("intellij.platform.editor.ex")
    embeddedModule("intellij.platform.editor.ui")

    embeddedModule("intellij.platform.projectModel")
    embeddedModule("intellij.platform.projectModel.impl")

    // Analysis modules needed by core platform modules
    embeddedModule("intellij.platform.analysis")
    embeddedModule("intellij.platform.analysis.impl")

    moduleSet(rpcMinimal())

    embeddedModule("intellij.platform.ide.core")
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

    embeddedModule("intellij.platform.macro")
    embeddedModule("intellij.platform.usageView.impl")

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

    embeddedModule("intellij.platform.statistics")
    embeddedModule("intellij.platform.statistics.config")
    embeddedModule("intellij.platform.statistics.uploader")
    embeddedModule("intellij.platform.experiment")
    embeddedModule("intellij.platform.ide.impl")

    embeddedModule("intellij.platform.rd.community")

    embeddedModule("intellij.platform.ide.ui.inspector")
    embeddedModule("intellij.platform.remote.core")
    embeddedModule("intellij.platform.ide.remote")
    embeddedModule("intellij.platform.threadDumpParser")
    embeddedModule("intellij.platform.ide.favoritesTreeView")
    // todo not used by platform - move to plugin
    embeddedModule("intellij.platform.ide.designer")

    embeddedModule("intellij.platform.bootstrap")

    // depends on intellij.platform.ide.impl
    module("intellij.platform.backend.workspace.impl")

    // Additional dependencies specific to lang.impl and ide.impl
    embeddedModule("intellij.platform.ide.concurrency")
    embeddedModule("intellij.platform.builtInServer")
    embeddedModule("intellij.platform.discoverability")
    embeddedModule("intellij.platform.externalSystem")
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

  // endregion
}
