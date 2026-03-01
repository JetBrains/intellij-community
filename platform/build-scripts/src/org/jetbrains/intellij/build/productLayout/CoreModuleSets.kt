// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreIde
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.coreLang
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.corePlatform
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.fleet
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.librariesIde
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcBackend
import org.jetbrains.intellij.build.productLayout.CoreModuleSets.rpcMinimal

/**
 * Core platform module sets forming the foundation of IntelliJ products.
 *
 * This file contains the base module sets that provide the platform infrastructure:
 * - **libraries***: Library modules (platform, IDE, ktor, misc)
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
    embeddedModule("intellij.libraries.jackson.annotations")
    embeddedModule("intellij.libraries.jackson")
    embeddedModule("intellij.libraries.jackson.jr.objects")
    embeddedModule("intellij.libraries.jackson.databind")
    embeddedModule("intellij.libraries.jackson.dataformat.yaml")
    embeddedModule("intellij.libraries.jackson.module.kotlin")
    embeddedModule("intellij.libraries.jackson3")
    embeddedModule("intellij.libraries.jackson3.jr.objects")
    embeddedModule("intellij.libraries.jackson3.databind")
    embeddedModule("intellij.libraries.jackson3.dataformat.yaml")
    embeddedModule("intellij.libraries.jackson3.module.kotlin")
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
    embeddedModule("intellij.libraries.snakeyaml")
    embeddedModule("intellij.libraries.snakeyaml.engine")
    embeddedModule("intellij.libraries.stream")
    embeddedModule("intellij.libraries.velocity")
    embeddedModule("intellij.libraries.xtext.xbase")
    embeddedModule("intellij.libraries.xz")
    // Temporary embedded while opentelemetry-exporter-otlp-common library remains embedded due to a dependency (IJPL-233394)
    embeddedModule("intellij.libraries.opentelemetry.sdk.autoconfigure.spi")
    embeddedModule("intellij.libraries.opentelemetry.exporter.sender.jdk")
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
    embeddedModule("intellij.libraries.jcef")
    embeddedModule("intellij.libraries.jediterm.core")
    embeddedModule("intellij.libraries.jediterm.ui")
    embeddedModule("intellij.libraries.jgoodies.common")
    embeddedModule("intellij.libraries.jgoodies.forms")
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

  /**
   * Miscellaneous library modules for specialized use cases.
   *
   * **Note:** All libs here must NOT be embedded. If embedded, move to `librariesPlatform()` or `librariesIde()`.
   *
   * **Typical use cases:** XML-RPC communication, CSV parsing, document storage
   * **Usage pattern:** Product-specific, not universally needed by all products
   */
  fun librariesMisc(): ModuleSet = moduleSet("libraries.misc") {
    // all libs here must not be embedded, if it is embedded, it should be moved to libs-core.xml
    module("intellij.libraries.javax.activation")
    module("intellij.libraries.xml.rpc")
    module("intellij.libraries.kotlinx.document.store.mvstore")
    module("intellij.libraries.opencsv")
    module("intellij.libraries.lucene.common")
    module("intellij.libraries.plexus.utils")
    module("intellij.libraries.maven.resolver.provider")
  }

  /**
   * Temporarily bundled library modules (planned to be removed).
   *
   * **⚠️ WARNING:** These are product-specific dependencies that should NOT be in core platform.
   *
   * **Current users:** Only DBE (DataGrip) - see jettison/xstream comments below
   * **Goal:** Remove from `corePlatform` and move to specific products that need them
   * **Typical NON-users:** Most products don't need these legacy libraries
   */
  fun librariesTemporaryBundled(): ModuleSet = moduleSet("libraries.temporaryBundled") {
    // Currently used only by DBE (see https://youtrack.jetbrains.com/issue/IJPL-211789/CNFE-org.codehaus.jettison.mapped.Configuration).
    // Declared as a dependency of xstream because xstream technically depends on it.
    // Marked as `embedded`: since xstream depends on it, this module must be embedded as well.
    // No other module should require jettison when using xstream; in general, avoid using xstream at all.
    embeddedModule("intellij.libraries.jettison")
    // lang-impl should not use it and embedded should be removed
    embeddedModule("intellij.libraries.xstream")
    module("intellij.libraries.commons.text")
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
   * @see essentialMinimal for lightweight IDE with editing (most IDE products should use this)
   */
  fun corePlatform(): ModuleSet = moduleSet("core.platform", selfContained = true, outputModule = "intellij.platform.ide.core", includeDependencies = true) {
    moduleSet(librariesPlatform())

    embeddedModule("intellij.platform.diagnostic.telemetry")

    embeddedModule("intellij.platform.util.ex")
    embeddedModule("intellij.platform.util.ui")
    embeddedModule("intellij.platform.util.coroutines")

    embeddedModule("intellij.platform.locking.impl")

    embeddedModule("intellij.platform.core")
    embeddedModule("intellij.platform.core.ui")
    embeddedModule("intellij.platform.core.impl")
    embeddedModule("intellij.platform.indexing")
    embeddedModule("intellij.platform.projectFrame")
    embeddedModule("intellij.platform.welcomeScreen")
    embeddedModule("intellij.platform.welcomeScreen.impl")

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
    embeddedModule("intellij.platform.util.diff")
    embeddedModule("fleet.andel")

    // Temporary: lang.impl incorrectly depends on xstream (should be removed)
    moduleSet(librariesTemporaryBundled())
  }

  // endregion

  // region Fleet and RPC

  fun fleet(): ModuleSet = moduleSet("fleet", includeDependencies = true) {
    // Same modules as fleet() - all are required
    embeddedModule("fleet.bifurcan")
    embeddedModule("fleet.fastutil")
    embeddedModule("fleet.kernel")
    embeddedModule("fleet.multiplatform.shims")
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
