// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets.librariesIde
import java.nio.file.Path

/**
 * Registry of community module sets that can be referenced programmatically by products.
 * These module sets correspond to XML module set files in META-INF/intellij.moduleSets.*.xml
 *
 * Products call these functions directly in ProductProperties.getProductContentModules() to create
 * ModuleSet instances that will be automatically injected into the product plugin.xml during build.
 *
 * @see <a href="../product-dsl/module-sets.md">Module Sets Documentation</a> - How module sets work and best practices
 * @see <a href="../product-dsl/programmatic-content.md">Programmatic Content Documentation</a> - How to use module sets in products
 */
object CommunityModuleSets  {
  /**
   * Main method to regenerate all community module set XML files from Kotlin definitions.
   * Run this whenever module sets are modified to keep XML files in sync.
   */
  @JvmStatic
  fun main(args: Array<String>) {
    @Suppress("TestOnlyProblems")
    val projectRoot = Path.of(PathManager.getHomePathFor(CommunityModuleSets::class.java)!!)

    generateAllModuleSets(
      obj = CommunityModuleSets,
      outputDir = projectRoot.resolve("community/platform/platform-resources/generated/META-INF"),
      label = "community",
      projectRoot = projectRoot,
      moduleOutputProvider = createModuleOutputProvider(projectRoot)
    )
  }

  /**
   * Core library modules.
   */
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
    embeddedModule("intellij.libraries.jackson")
    embeddedModule("intellij.libraries.jackson.jr.objects")
    embeddedModule("intellij.libraries.jackson.databind")
    embeddedModule("intellij.libraries.jackson.dataformat.yaml")
    embeddedModule("intellij.libraries.jackson.module.kotlin")
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
    embeddedModule("intellij.libraries.oro.matcher")
    embeddedModule("intellij.libraries.proxy.vole")
    embeddedModule("intellij.libraries.rhino")
    embeddedModule("intellij.libraries.snakeyaml")
    embeddedModule("intellij.libraries.snakeyaml.engine")
    embeddedModule("intellij.libraries.stream")
    embeddedModule("intellij.libraries.velocity")
    embeddedModule("intellij.libraries.xtext.xbase")
    embeddedModule("intellij.libraries.xz")
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
    // todo - once intellij.java.aetherDependencyResolver will be migrated to product module, it can be a non-embedded module (see ArtifactRepositoryManager)
    embeddedModule("intellij.libraries.plexus.utils")
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
    // Include essential first (which includes libraries)
    moduleSet(CoreModuleSets.essential())
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
}