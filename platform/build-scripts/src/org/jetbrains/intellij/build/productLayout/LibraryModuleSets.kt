// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection")

package org.jetbrains.intellij.build.productLayout

/**
 * Library module sets: every `intellij.libraries.*` wrapper that a shared module set ships.
 *
 * This file holds only library wrappers. A set that holds platform modules lives in [CoreModuleSets],
 * [CommunityModuleSets], or the ultimate `UltimateModuleSets`, and nests a set from here.
 * This object depends on no other module set object.
 *
 * - **librariesPlatform**: universal utilities for every product; nests Jackson and Ktor
 * - **librariesIde**: UI libraries for a product with a user interface
 * - **librariesOpenTelemetry / librariesOpenTelemetryExporter**: the OpenTelemetry API, SDK, and exporters
 * - **librariesLsp4j / librariesDap**: the Eclipse LSP4J wrappers for LSP and DAP
 * - **librariesIdeCommon**: libraries that only plugins consume, with no platform owner
 * - **librariesGrpc**: the gRPC runtime
 *
 * **A shared module set is the last resort for a library module.** Take the lowest route that works.
 * A library that one plugin uses belongs to that plugin, as private content. A library whose types cross
 * a plugin boundary belongs to the plugin that owns the API, and every dependent plugin reuses that copy.
 * Only when neither route works does the module join a set here, because a set ships it to every product.
 * Read `build/decisions/0005-a-library-copy-belongs-to-the-plugin-that-owns-its-api.md`.
 *
 * **How to regenerate XML files:**
 * - IDE: Run configuration "Generate Product Layouts"
 * - Bazel: `bazel run //platform/buildScripts:plugin-model-tool`
 */
object LibraryModuleSets {
  /**
   * Core platform library modules required by ALL products including analysis tools.
   * Contains universal utilities: serialization, compression, collections, parsing, networking.
   *
   * **Typical users:** All products (CodeServer, IDEA, PyCharm, etc.)
   *
   * **Membership vs loading:** membership in this set means "available in every product"; `embeddedModule`
   * versus `module` decides whether the library *also* enters the core classloader. Demote a library in
   * place — keep it next to its family instead of moving it to another set.
   *
   * **Note:** UI/IDE-specific libraries (JCEF, Jediterm, PTY4J, SSH) have been moved to `librariesIde()`,
   * and the OpenTelemetry wrappers to `CoreModuleSets.telemetry()` / `CoreModuleSets.telemetryImpl()` - they
   * belong next to the telemetry modules that are their only reason to exist.
   *
   * @see librariesIde for UI and IDE-specific libraries
   * @see CoreModuleSets.telemetry for the OpenTelemetry API/SDK wrappers and the telemetry API
   */
  fun librariesPlatform(): ModuleSet = moduleSet("libraries.platform") {
    embeddedModule("intellij.libraries.java.compatibility")
    embeddedModule("intellij.libraries.jetbrains.annotations")

    embeddedModule("intellij.libraries.kotlin.reflect")
    // core, because no plugin can own the copy: the Java, Kotlin and API Watcher plugins all need it, and four
    // products bundle the Kotlin plugin without the Java plugin. Not embedded: only plugin content reads it.
    module("intellij.libraries.kotlin.metadata")
    // intellij.platform.wsl.impl and intellij.platform.util.http uses it
    embeddedModule("intellij.libraries.kotlinx.io")

    // not embedded: only plugin content needs CBOR, which also settles the old
    // "JB Client should not embed intellij.platform.split" todo for the core classloader
    module("intellij.libraries.kotlinx.serialization.cbor")

    embeddedModule("intellij.libraries.kotlinx.serialization.core")
    embeddedModule("intellij.libraries.kotlinx.serialization.json")
    embeddedModule("intellij.libraries.kotlinx.serialization.protobuf")
    embeddedModule("intellij.libraries.kotlinx.collections.immutable")
    embeddedModule("intellij.libraries.kotlinx.datetime")
    embeddedModule("intellij.libraries.kotlinx.html")
    // kotlinx-coroutines libraries
    embeddedModule("intellij.libraries.kotlinx.coroutines.core")
    embeddedModule("intellij.libraries.kotlinx.coroutines.debug")
    // Space plugin uses it and bundles into IntelliJ IDEA, but not bundles into DataGrip, so, or Space plugin should bundle this lib,
    // or IJ Platform. As it is a small library and consistency is important across other coroutine libs, bundle to IJ Platform.
    // note 2: despite what we use as "used by", AIA tests broken —
    //   com.intellij.ml.llm.end2end.tests.agent.AiAgentSmokeTest.No error in AI agents communication
    //     java.lang.NoClassDefFoundError: kotlinx/coroutines/slf4j/MDCContext
    //      at io.ktor.client.plugins.observer.ResponseObserverContextJvmKt.getResponseObserverContext(ResponseObserverContextJvm.kt:11)
    // so, we embed it
    embeddedModule("intellij.libraries.kotlinx.coroutines.slf4j")
    module("intellij.libraries.kotlinx.coroutines.guava")
    embeddedModule("intellij.libraries.aalto.xml")
    embeddedModule("intellij.libraries.asm")
    module("intellij.libraries.asm.tools")
    embeddedModule("intellij.libraries.automaton")
    embeddedModule("intellij.libraries.bouncy.castle.provider")
    embeddedModule("intellij.libraries.bouncy.castle.pgp")
    embeddedModule("intellij.libraries.blockmap")
    embeddedModule("intellij.libraries.caffeine")
    embeddedModule("intellij.libraries.classgraph")
    embeddedModule("intellij.libraries.cli.parser")
    embeddedModule("intellij.libraries.commons.cli")
    // embedded because embedded library content needs them in the core classloader:
    // `commons-compress` calls into both (`ArchiveInputStream`, `FramedLZ4CompressorInputStream`) and `batik`
    // (via `xmlgraphics-commons`) calls into commons-io. Both exclude the artifacts and depend on these wrappers.
    embeddedModule("intellij.libraries.commons.codec")
    embeddedModule("intellij.libraries.commons.compress")
    embeddedModule("intellij.libraries.commons.io")
    embeddedModule("intellij.libraries.commons.imaging")
    embeddedModule("intellij.libraries.commons.lang3")
    embeddedModule("intellij.libraries.commons.logging")
    embeddedModule("intellij.libraries.fastutil")
    // embedded because the jar splits the package `com.intellij.internal.statistic.eventLog.validator` with
    // `intellij.platform.statistics`, which is embedded itself. A split package needs one classloader.
    embeddedModule("intellij.libraries.fus.ap.validation")
    embeddedModule("intellij.libraries.gson")
    embeddedModule("intellij.libraries.guava")
    embeddedModule("intellij.libraries.hash4j")
    embeddedModule("intellij.libraries.hdr.histogram")
    embeddedModule("intellij.libraries.http.client")
    embeddedModule("intellij.libraries.imgscalr")
    embeddedModule("intellij.libraries.ini4j")
    embeddedModule("intellij.libraries.ion")
    moduleSet(librariesJackson2())
    moduleSet(librariesJackson3())
    moduleSet(librariesKtor())

    module("intellij.libraries.java.websocket")
    embeddedModule("intellij.libraries.javax.annotation")
    // used by intellij.platform.util.jdom, so, embedded
    embeddedModule("intellij.libraries.jaxen")
    embeddedModule("intellij.libraries.jbr")
    embeddedModule("intellij.libraries.jcip")
    module("intellij.libraries.jna")
    embeddedModule("intellij.libraries.jsoup")
    module("intellij.libraries.jsonpath")
    embeddedModule("intellij.libraries.jsvg")
    module("intellij.libraries.jvm.native.trusted.roots")
    module("intellij.libraries.jzlib")
    embeddedModule("intellij.libraries.kryo5")
    embeddedModule("intellij.libraries.lz4")
    embeddedModule("intellij.libraries.markdown")
    embeddedModule("intellij.libraries.mvstore")

    embeddedModule("intellij.libraries.netty.buffer")
    embeddedModule("intellij.libraries.netty.codec.compression")
    embeddedModule("intellij.libraries.netty.codec.http")
    embeddedModule("intellij.libraries.netty.codec.protobuf")
    module("intellij.libraries.netty.handler.proxy")

    embeddedModule("intellij.libraries.oro.matcher")
    embeddedModule("intellij.libraries.protobuf")
    module("intellij.libraries.protobuf.kotlin")
    module("intellij.libraries.protobuf.java.util")
    module("intellij.libraries.proxy.vole")
    module("intellij.libraries.rhino")
    module("intellij.libraries.semver")
    embeddedModule("intellij.libraries.snakeyaml")
    embeddedModule("intellij.libraries.snakeyaml.engine")
    embeddedModule("intellij.libraries.stream")
    // not embedded: consumed by non-embedded platform content (smRunner, buildScripts.downloader) and by plugins
    module("intellij.libraries.teamcity.service.messages")
    embeddedModule("intellij.libraries.velocity")
    embeddedModule("intellij.libraries.xtext.xbase")
    embeddedModule("intellij.libraries.xz")
  }

  /**
   * OpenTelemetry API and SDK library wrappers.
   *
   * A nested set rather than plain members of `CoreModuleSets.telemetry()`, matching how
   * `CoreModuleSets.telemetryImpl()` nests `librariesOpenTelemetryExporter()`: library wrappers must never pick up
   * a set's `includeDependencies` default, since their own JPS deps are other wrappers and packing those would
   * bundle them twice. `telemetry()` doesn't set the flag; `telemetryImpl()` still does.
   */
  fun librariesOpenTelemetry(): ModuleSet = moduleSet("libraries.opentelemetry") {
    embeddedModule("intellij.libraries.opentelemetry")
    embeddedModule("intellij.libraries.opentelemetry.extension.kotlin")
    embeddedModule("intellij.libraries.opentelemetry.semconv")
  }

  /**
   * OpenTelemetry exporter library wrappers - the OTLP exporter plus the SPI and HTTP sender it loads at runtime.
   *
   * Nested in `CoreModuleSets.telemetryImpl()` for the same `includeDependencies` reason as `librariesOpenTelemetry()`.
   */
  fun librariesOpenTelemetryExporter(): ModuleSet = moduleSet("libraries.opentelemetry.exporter") {
    embeddedModule("intellij.libraries.opentelemetry.exporter.otlp.common")
    embeddedModule("intellij.libraries.opentelemetry.sdk.autoconfigure.spi")
    embeddedModule("intellij.libraries.opentelemetry.exporter.sender.jdk")
  }

  /**
   * Eclipse LSP4J library wrapper modules used by LSP and DAP support.
   *
   * Kept separate from `librariesPlatform()` because LSP4J is not a universal platform dependency.
   * Kept embedded because LSP support modules are embedded.
   */
  fun librariesLsp4j(): ModuleSet = moduleSet("libraries.lsp4j", outputModule = "intellij.platform.lsp") {
    embeddedModule("intellij.libraries.eclipse.lsp4j")
    embeddedModule("intellij.libraries.eclipse.lsp4j.jsonrpc")
  }

  /**
   * Eclipse LSP4J debug (DAP) library wrapper modules.
   *
   * `intellij.platform.dap` moved to the JetBrains DAP protocol library and no longer uses these wrappers.
   * The CIDR DAP client still does, and CLion does not include `lsp()`, so the set stays separate from `librariesLsp4j()`.
   *
   * Not embedded, and nested in `CoreModuleSets.corePlatform()`: available everywhere, outside the core classloader.
   */
  fun librariesDap(): ModuleSet = moduleSet("libraries.dap") {
    module("intellij.libraries.eclipse.lsp4j.debug")
    module("intellij.libraries.eclipse.lsp4j.jsonrpc.debug")
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
    module("intellij.libraries.jackson.dataformat.yaml")
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
    module("intellij.libraries.jackson3.dataformat.yaml")
    module("intellij.libraries.jackson3.dataformat.toml")
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
    module("intellij.libraries.jsch.agent.proxy")
    embeddedModule("intellij.libraries.miglayout.swing")
    module("intellij.libraries.pty4j")
    module("intellij.libraries.sshj")
    embeddedModule("intellij.libraries.swingx")
    embeddedModule("intellij.libraries.winp")

    embeddedModule("intellij.libraries.rd.core")
    embeddedModule("intellij.libraries.rd.framework")
    embeddedModule("intellij.libraries.rd.swing")
    embeddedModule("intellij.libraries.rd.text")
  }

  /**
   * Ktor library wrapper modules.
   *
   * **Typical use cases:** RPC infrastructure, Remote Dev, Fleet backend, HTTP-based integrations
   *
   * Kept as a dedicated module set so the family stays bumpable and reviewable in one place, and so a
   * product can reason about Ktor as a unit.
   *
   * The CIO engines are not embedded: only plugin content instantiates them, so they stay available
   * everywhere without occupying the core classloader.
   *
   * Included transitively by `librariesPlatform()`.
   */
  fun librariesKtor(): ModuleSet = moduleSet("libraries.ktor") {
    embeddedModule("intellij.libraries.kotlinx.serialization.json.io")
    embeddedModule("intellij.libraries.ktor.io")
    embeddedModule("intellij.libraries.ktor.utils")
    embeddedModule("intellij.libraries.ktor.network.tls")
    embeddedModule("intellij.libraries.ktor.client")
    module("intellij.libraries.ktor.client.cio")
    module("intellij.libraries.ktor.server.cio")
  }

  /**
   * Libraries that only plugins consume, with no platform owner.
   * Each entry is a shared-set placement under ADR 0005 that a plugin-private copy could replace.
   */
  fun librariesIdeCommon(): ModuleSet = moduleSet("libraries.ide.common") {
    module("intellij.libraries.javax.activation")
    module("intellij.libraries.opencsv")
    module("intellij.libraries.squareup.okio.jvm")
    module("intellij.libraries.jettison")
    module("intellij.libraries.xstream")
    module("intellij.libraries.commons.text")
  }

  /**
   * gRPC runtime, used by the process mediator, IJent, and many plugins.
   */
  fun librariesGrpc(): ModuleSet = moduleSet("libraries.grpc") {
    module("intellij.libraries.grpc")
    module("intellij.libraries.grpc.netty.shaded")
  }
}
