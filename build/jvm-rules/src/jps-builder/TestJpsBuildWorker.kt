// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.jps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.walk

object TestJpsBuildWorker {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    configureGlobalJps()

    val userHomeDir = Path.of(System.getProperty("user.home"))
    val out = StringWriter()
    try {
      @Suppress("SpellCheckingInspection")
      val baseDir = Path.of("/private/var/tmp/_bazel_develar/c002af20f6ada3e2667e9e2ceaf2ceca/execroot/_main")

      //val sources = Files.newDirectoryStream(userHomeDir.resolve("projects/idea/community/platform/util/xmlDom/src")).use { it.toList() }
      val ideaProjectDirName = if (Runtime.getRuntime().availableProcessors() >= 20) "idea-push" else "idea-second"
      val communityDir = userHomeDir.resolve("projects/$ideaProjectDirName/community")
      val sources = communityDir.resolve("platform/platform-impl/src")
        .walk()
        .filter {
          val p = it.toString()
          p.endsWith(".kt") || p.endsWith(".java")
        }
        .map { "../community+/" + communityDir.relativize(it).invariantSeparatorsPathString }
        .sorted()
        .map { baseDir.resolve(it).normalize() }
        .toList()

      require(sources.isNotEmpty())

      runBlocking(Dispatchers.Default) {
        val args = parseArgs(testParams.trimStart().lines().toTypedArray())
        val messageDigest = MessageDigest.getInstance("SHA-256")
        buildUsingJps(
          baseDir = baseDir,
          args = args,
          out = out,
          sources = sources,
          dependencyFileToDigest = args.optionalList(JvmBuilderFlags.CLASSPATH).associate {
            val file = baseDir.resolve(it).normalize()
            val digest = messageDigest.digest(Files.readAllBytes(file))
            messageDigest.reset()
            file to digest
          },
          isDebugEnabled = true,
        )
      }
    }
    finally {
      System.out.append(out.toString())
    }
  }
}

@Suppress("SpellCheckingInspection")
private const val testParams = """
--target_label
@community//platform/platform-impl:ide-impl
--rule_kind
kt_jvm_library
--kotlin_module_name
intellij.platform.ide.impl
--add-export
java.desktop/sun.awt=ALL-UNNAMED
java.desktop/sun.font=ALL-UNNAMED
java.desktop/java.awt.peer=ALL-UNNAMED
jdk.attach/sun.tools.attach=ALL-UNNAMED
java.desktop/sun.awt.image=ALL-UNNAMED
java.desktop/sun.awt.datatransfer=ALL-UNNAMED
java.desktop/sun.swing=ALL-UNNAMED
java.base/sun.nio.fs=ALL-UNNAMED
--out
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-impl/ide-impl.jar
--abi-out
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-impl/ide-impl.abi.jar
--warn
off
--jvm-default
all
--jvm-target
17
--opt_in
com.intellij.openapi.util.IntellijInternalApi
org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
--classpath
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/eawtstub/lib+/eawtstub-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-api/ide.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/util.abi.jar
../lib++_repo_rules+annotations-24_0_0_http/file/annotations-24.0.0.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util-rt/util-rt-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/base/base.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/core-api/core.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/extensions/extensions.abi.jar
../lib++_repo_rules+kotlinx-coroutines-core-jvm-1_8_0-intellij-11_http/file/kotlinx-coroutines-core-jvm-1.8.0-intellij-11.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/forms_rt/java-guiForms-rt-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/projectModel-api/projectModel.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/jps/model-api/model-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/analysis-api/analysis.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/editor-ui-api/editor.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/credential-store/credentialStore.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/remote-core/remote-core.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ide-core/ide-core.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/progress/shared/ide-progress.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/oro_matcher/lib++_repo_rules+oro-2_0_8_http/file/oro-2.0.8-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/lang-api/lang.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/lang-core/lang-core.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/lvcs-api/lvcs.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/indexing-api/indexing.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/code-style-api/codeStyle.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/execution/execution.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/refactoring/refactoring.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jna-platform-5_14_0_http_import/lib++_repo_rules+jna-platform-5_14_0_http/file/jna-platform-5.14.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jna-5_14_0_http_import/lib++_repo_rules+jna-5_14_0_http/file/jna-5.14.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/winp/lib++_repo_rules+winp-1_30_1_http/file/winp-1.30.1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/swingx/lib++_repo_rules+swingx-core-1_6_2-2_http/file/swingx-core-1.6.2-2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/core-impl/core-impl.abi.jar
../lib++_repo_rules+kotlin-stdlib-2_1_0_http/file/kotlin-stdlib-2.1.0.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/miglayout-swing-11_4_http_import/lib++_repo_rules+miglayout-swing-11_4_http/file/miglayout-swing-11.4-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/miglayout-core-11_4_http_import/lib++_repo_rules+miglayout-core-11_4_http/file/miglayout-core-11.4-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/projectModel-impl/projectModel-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/jps/model-serialization/model-serialization-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util-ex/util-ex.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/concurrency/concurrency.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/workspace/storage/storage.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/workspace/jps/jps.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/commons-imaging/lib++_repo_rules+commons-imaging-1_0-RC-1_http/file/commons-imaging-1.0-RC-1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/guava-33_3_1-jre_http_import/lib++_repo_rules+guava-33_3_1-jre_http/file/guava-33.3.1-jre-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/failureaccess-1_0_2_http_import/lib++_repo_rules+failureaccess-1_0_2_http/file/failureaccess-1.0.2-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/j2objc-annotations-3_0_0_http_import/lib++_repo_rules+j2objc-annotations-3_0_0_http/file/j2objc-annotations-3.0.0-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/jps/model-impl/model-impl-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/analysis-impl/analysis-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/editor-ui-ex/editor-ex.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/gson/lib++_repo_rules+gson-2_11_0_http/file/gson-2.11.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/httpmime-4_5_14_http_import/lib++_repo_rules+httpmime-4_5_14_http/file/httpmime-4.5.14-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/httpclient-4_5_14_http_import/lib++_repo_rules+httpclient-4_5_14_http/file/httpclient-4.5.14-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/httpcore-4_4_16_http_import/lib++_repo_rules+httpcore-4_4_16_http/file/httpcore-4.4.16-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diff-api/diff.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/diff/diff.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/imgscalr/lib++_repo_rules+imgscalr-lib-4_2_http/file/imgscalr-lib-4.2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/built-in-server-api/builtInServer.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/observable/ide-observable.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/stream_ex/lib++_repo_rules+streamex-0_8_2_http/file/streamex-0.8.2-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-codec-http2-4_2_0_RC1_http_import/lib++_repo_rules+netty-codec-http2-4_2_0_RC1_http/file/netty-codec-http2-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-transport-4_2_0_RC1_http_import/lib++_repo_rules+netty-transport-4_2_0_RC1_http/file/netty-transport-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-resolver-4_2_0_RC1_http_import/lib++_repo_rules+netty-resolver-4_2_0_RC1_http/file/netty-resolver-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-codec-4_2_0_RC1_http_import/lib++_repo_rules+netty-codec-4_2_0_RC1_http/file/netty-codec-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-codec-base-4_2_0_RC1_http_import/lib++_repo_rules+netty-codec-base-4_2_0_RC1_http/file/netty-codec-base-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-handler-4_2_0_RC1_http_import/lib++_repo_rules+netty-handler-4_2_0_RC1_http/file/netty-handler-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-transport-native-unix-common-4_2_0_RC1_http_import/lib++_repo_rules+netty-transport-native-unix-common-4_2_0_RC1_http/file/netty-transport-native-unix-common-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-codec-http-4_2_0_RC1_http_import/lib++_repo_rules+netty-codec-http-4_2_0_RC1_http/file/netty-codec-http-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jackson/lib++_repo_rules+jackson-core-2_17_0_http/file/jackson-core-2.17.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/java_compatibility/lib++_repo_rules+java-compatibility-1_0_1_http/file/java-compatibility-1.0.1-ijar.jar
../lib++_repo_rules+kotlin-reflect-2_1_0_http/file/kotlin-reflect-2.1.0.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jackson-databind-2_17_2_http_import/lib++_repo_rules+jackson-databind-2_17_2_http/file/jackson-databind-2.17.2-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jackson-annotations-2_17_2_http_import/lib++_repo_rules+jackson-annotations-2_17_2_http/file/jackson-annotations-2.17.2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/util-ui.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-util-io-impl/ide-util-io-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-util-io/ide-util-io.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/instanceContainer/instanceContainer.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/service-container/serviceContainer.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jcef/lib++_repo_rules+jcef-122_1_9-gd14e051-chromium-122_0_6261_94-api-1_17-251-b2_http/file/jcef-122.1.9-gd14e051-chromium-122.0.6261.94-api-1.17-251-b2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/statistics/statistics.abi.jar
../lib++_repo_rules+ap-validation-76_http/file/ap-validation-76.jar
../lib++_repo_rules+model-76_http/file/model-76.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/asm/lib++_repo_rules+asm-all-9_6_1_http/file/asm-all-9.6.1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jsoup/lib++_repo_rules+jsoup-1_18_1_http/file/jsoup-1.18.1-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/rd-platform-community/rd-community.abi.jar
../lib++_repo_rules+rd-core-2024_3_1_http/file/rd-core-2024.3.1.jar
../lib++_repo_rules+rd-framework-2024_3_1_http/file/rd-framework-2024.3.1.jar
../lib++_repo_rules+rd-swing-2024_3_1_http/file/rd-swing-2024.3.1.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/fastutil-min/lib++_repo_rules+intellij-deps-fastutil-8_5_14-jb1_http/file/intellij-deps-fastutil-8.5.14-jb1-ijar.jar
../lib++_repo_rules+blockmap-1_0_7_http/file/blockmap-1.0.7.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util-class-loader/util-classLoader-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-buffer-4_2_0_RC1_http_import/lib++_repo_rules+netty-buffer-4_2_0_RC1_http/file/netty-buffer-4.2.0.RC1-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/netty-common-4_2_0_RC1_http_import/lib++_repo_rules+netty-common-4_2_0_RC1_http/file/netty-common-4.2.0.RC1-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/progress/progress.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/core-ui/core-ui.abi.jar
../lib++_repo_rules+marketplace-zip-signer-0_1_24_http/file/marketplace-zip-signer-0.1.24.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/caffeine/lib++_repo_rules+caffeine-3_1_8_http/file/caffeine-3.1.8-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/classgraph/lib++_repo_rules+classgraph-4_8_174_http/file/classgraph-4.8.174-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/zip/zip-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/icu4j/lib++_repo_rules+icu4j-73_2_http/file/icu4j-73.2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/xmlDom/xmlDom.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ide-core-impl/ide-core-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ide-core/plugins/ide-core-plugins.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-util-netty/ide-util-netty.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/aalto-xml-1_3_3_http_import/lib++_repo_rules+aalto-xml-1_3_3_http/file/aalto-xml-1.3.3-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/stax2-api-4_2_2_http_import/lib++_repo_rules+stax2-api-4_2_2_http/file/stax2-api-4.2.2-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jbr-api/lib++_repo_rules+jbr-api-1_0_0_http/file/jbr-api-1.0.0-ijar.jar
../lib++_repo_rules+kotlinx-serialization-json-jvm-1_7_3_http/file/kotlinx-serialization-json-jvm-1.7.3.jar
../lib++_repo_rules+kotlinx-serialization-core-jvm-1_7_3_http/file/kotlinx-serialization-core-jvm-1.7.3.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/jdom/jdom-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jvm-native-trusted-roots/lib++_repo_rules+jvm-native-trusted-roots-1_0_21_http/file/jvm-native-trusted-roots-1.0.21-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-sdk-1_45_0_http_import/lib++_repo_rules+opentelemetry-sdk-1_45_0_http/file/opentelemetry-sdk-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-api-1_45_0_http_import/lib++_repo_rules+opentelemetry-api-1_45_0_http/file/opentelemetry-api-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-context-1_45_0_http_import/lib++_repo_rules+opentelemetry-context-1_45_0_http/file/opentelemetry-context-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-sdk-common-1_45_0_http_import/lib++_repo_rules+opentelemetry-sdk-common-1_45_0_http/file/opentelemetry-sdk-common-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-sdk-trace-1_45_0_http_import/lib++_repo_rules+opentelemetry-sdk-trace-1_45_0_http/file/opentelemetry-sdk-trace-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-api-incubator-1_45_0-alpha_http_import/lib++_repo_rules+opentelemetry-api-incubator-1_45_0-alpha_http/file/opentelemetry-api-incubator-1.45.0-alpha-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-sdk-metrics-1_45_0_http_import/lib++_repo_rules+opentelemetry-sdk-metrics-1_45_0_http/file/opentelemetry-sdk-metrics-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-sdk-logs-1_45_0_http_import/lib++_repo_rules+opentelemetry-sdk-logs-1_45_0_http/file/opentelemetry-sdk-logs-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-semconv/lib++_repo_rules+opentelemetry-semconv-1_28_0-alpha_http/file/opentelemetry-semconv-1.28.0-alpha-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/telemetry/telemetry.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/diagnostic.abi.jar
../lib++_repo_rules+opentelemetry-extension-kotlin-1_45_0_http/file/opentelemetry-extension-kotlin-1.45.0.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/hdr_histogram/lib++_repo_rules+HdrHistogram-2_2_2_http/file/HdrHistogram-2.2.2-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/code-style-impl/codeStyle-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/text-matching/text-matching-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-impl/rpc/ide-rpc.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/hash4j/lib++_repo_rules+hash4j-0_19_0_http/file/hash4j-0.19.0-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/backend/workspace/workspace.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/startUpPerformanceReporter/startUpPerformanceReporter.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/telemetry-impl/telemetry-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/telemetry.exporters/telemetry-exporters.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/telemetry/rt/diagnostic-telemetry-rt-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ijent/ijent.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/eel/eel.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/backend/observation/observation.abi.jar
../lib++_repo_rules+pty4j-0_13_1_http/file/pty4j-0.13.1.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/settings/settings.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/coroutines/coroutines.abi.jar
../lib++_repo_rules+rwmutex-idea-0_0_7_http/file/rwmutex-idea-0.0.7.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/lz4-java/lib++_repo_rules+lz4-java-1_8_0_http/file/lz4-java-1.8.0-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ml-api/ml.abi.jar
../lib++_repo_rules+extension-34_http/file/extension-34.jar
../lib++_repo_rules+kotlinx-collections-immutable-jvm-0_3_8_http/file/kotlinx-collections-immutable-jvm-0.3.8.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/storages/io-storages-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/core-nio-fs/libcore-nio-fs-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ijent/impl/community-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ijent/buildConstants/community-buildConstants.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/kernel/shared/kernel.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/kernel/rpc/rpc.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/rpc/rpc.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/kernel/kernel.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/rhizomedb/rhizomedb.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/util/core/fleet-util-core.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/util/logging/api/fleet-util-logging-api.abi.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/slf4j-api/lib++_repo_rules+slf4j-api-2_0_13_http/file/slf4j-api-2.0.13-ijar.jar
../lib++_repo_rules+kotlinx-coroutines-slf4j-1_8_0-intellij-11_http/file/kotlinx-coroutines-slf4j-1.8.0-intellij-11.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/project/shared/project.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/jbr/jbr-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ui.jcef/ui-jcef.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/eel-provider/eel-provider.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-impl/ui/ide-ui.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/buildData/buildData.abi.jar
../lib++_repo_rules+annotations-java5-24_0_0_http/file/annotations-java5-24.0.0.jar
../lib++_repo_rules+kotlinx-coroutines-debug-1_8_0-intellij-11_http/file/kotlinx-coroutines-debug-1.8.0-intellij-11.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jaxen/lib++_repo_rules+jaxen-1_2_0_http/file/jaxen-1.2.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/log4_j/lib++_repo_rules+log4j-over-slf4j-1_7_36_http/file/log4j-over-slf4j-1.7.36-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/rt-java8/rt-java8-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/commons-compress/lib++_repo_rules+commons-compress-1_26_1_http/file/commons-compress-1.26.1-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/troveCompileOnly/troveCompileOnly-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/kryo5/lib++_repo_rules+kryo5-5_6_0_http/file/kryo5-5.6.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jcip/lib++_repo_rules+jcip-annotations-1_0_http/file/jcip-annotations-1.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/mvstore/lib++_repo_rules+h2-mvstore-2_3_232_http/file/h2-mvstore-2.3.232-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/jsvg/lib++_repo_rules+jsvg-1_3_0-jb_8_http/file/jsvg-1.3.0-jb.8-ijar.jar
../lib++_repo_rules+kotlinx-serialization-protobuf-jvm-1_7_3_http/file/kotlinx-serialization-protobuf-jvm-1.7.3.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/automaton/lib++_repo_rules+automaton-1_12-4_http/file/automaton-1.12-4-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/reporting/api/fleet-reporting-api.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/preferences/preferences.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/util/os/fleet-util-os-hjar.jar
../lib++_repo_rules+expects-compiler-plugin-2_1_0-0_3_http/file/expects-compiler-plugin-2.1.0-0.3.jar
../lib++_repo_rules+rpc-compiler-plugin-2_1_0-0_4_http/file/rpc-compiler-plugin-2.1.0-0.4.jar
../lib++_repo_rules+rhizomedb-compiler-plugin-2_1_0-0_2_http/file/rhizomedb-compiler-plugin-2.1.0-0.2.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/usageView/usageView.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/statistics/uploader/uploader.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/statistics/config/config.abi.jar
../lib++_repo_rules+jackson-module-kotlin-2_17_2_http/file/jackson-module-kotlin-2.17.2.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/runtime/product/product.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/runtime/repository/repository-hjar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/indexing-impl/indexing-impl.abi.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/nanoxml/nanoxml-hjar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/proxy-vole-1_1_6_http_import/lib++_repo_rules+proxy-vole-1_1_6_http/file/proxy-vole-1.1.6-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/delight-rhino-sandbox-0_0_17_http_import/lib++_repo_rules+delight-rhino-sandbox-0_0_17_http/file/delight-rhino-sandbox-0.0.17-ijar.jar
../lib++_repo_rules+rd-text-2024_3_1_http/file/rd-text-2024.3.1.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-exporter-otlp-common-1_45_0_http_import/lib++_repo_rules+opentelemetry-exporter-otlp-common-1_45_0_http/file/opentelemetry-exporter-otlp-common-1.45.0-ijar.jar
bazel-out/lib+/darwin_arm64-fastbuild/bin/_ijar/opentelemetry-exporter-common-1_45_0_http_import/lib++_repo_rules+opentelemetry-exporter-common-1_45_0_http/file/opentelemetry-exporter-common-1.45.0-ijar.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/http/http.abi.jar
../lib++_repo_rules+ktor-client-core-jvm-2_3_13_http/file/ktor-client-core-jvm-2.3.13.jar
../lib++_repo_rules+ktor-http-jvm-2_3_13_http/file/ktor-http-jvm-2.3.13.jar
../lib++_repo_rules+ktor-utils-jvm-2_3_13_http/file/ktor-utils-jvm-2.3.13.jar
../lib++_repo_rules+ktor-io-jvm-2_3_13_http/file/ktor-io-jvm-2.3.13.jar
../lib++_repo_rules+ktor-events-jvm-2_3_13_http/file/ktor-events-jvm-2.3.13.jar
../lib++_repo_rules+ktor-websocket-serialization-jvm-2_3_13_http/file/ktor-websocket-serialization-jvm-2.3.13.jar
../lib++_repo_rules+ktor-serialization-jvm-2_3_13_http/file/ktor-serialization-jvm-2.3.13.jar
../lib++_repo_rules+ktor-websockets-jvm-2_3_13_http/file/ktor-websockets-jvm-2.3.13.jar
../lib++_repo_rules+ktor-client-java-jvm-2_3_13_http/file/ktor-client-java-jvm-2.3.13.jar
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/eel-impl/eel-impl.abi.jar
--deps_artifacts
bazel-out/community+/darwin_arm64-fastbuild/bin/jps/model-impl/model-impl.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/observable/ide-observable-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/rd-platform-community/rd-community-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util-class-loader/util-classLoader.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/zip/zip.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/xmlDom/xmlDom-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/jdom/jdom.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/text-matching/text-matching.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/platform-impl/rpc/ide-rpc-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/diagnostic/startUpPerformanceReporter/startUpPerformanceReporter-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/backend/observation/observation-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/ml-api/ml-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/util/storages/io-storages.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/core-nio-fs/libcore-nio-fs.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/project/shared/project-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/jbr/jbr.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/fleet/util/core/fleet-util-core-kt.jdeps
bazel-out/community+/darwin_arm64-fastbuild/bin/platform/buildData/buildData-kt.jdeps
--plugin-id
org.jetbrains.kotlin.kotlin-serialization-compiler-plugin
--plugin-classpath
"""