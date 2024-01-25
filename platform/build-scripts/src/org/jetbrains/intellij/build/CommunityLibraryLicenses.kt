// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.LibraryLicense.Companion.jetbrainsLibrary
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers

/**
 * Defines information about licenses of libraries located in 'community', 'contrib' and 'android' repositories.
 */
object CommunityLibraryLicenses {
  @JvmStatic
  @Suppress("SpellCheckingInspection", "NonAsciiCharacters")
  val LICENSES_LIST: List<LibraryLicense> = java.util.List.of(
    LibraryLicense(name = "A fast Java JSON schema validator", libraryName = "json-schema-validator",
                   url = "https://github.com/networknt/json-schema-validator")
      .apache("https://github.com/networknt/json-schema-validator/blob/master/LICENSE"),
    LibraryLicense(name = "aalto-xml", libraryName = "aalto-xml", url = "https://github.com/FasterXML/aalto-xml/")
      .apache("https://github.com/FasterXML/aalto-xml/blob/master/LICENSE"),
    androidDependency(name = "AAPT Protos", libraryName = "aapt-proto"),
    LibraryLicense(name = "AhoCorasickDoubleArrayTrie", libraryName = "com.hankcs:aho-corasick-double-array-trie",
                   url = "https://github.com/hankcs/AhoCorasickDoubleArrayTrie")
      .apache("https://github.com/hankcs/AhoCorasickDoubleArrayTrie#license")
      .suppliedByPersons("hankcs"),
    LibraryLicense(name = "Allure java commons", libraryName = "io.qameta.allure.java.commons",
                   url = "https://github.com/allure-framework/allure-java")
      .apache("https://github.com/allure-framework/allure-java/blob/master/README.md"),
    androidDependency(name = "Am Instrument Data proto", libraryName = "libam-instrumentation-data-proto"),
    LibraryLicense(name = "Amazon Ion Java", libraryName = "ion", url = "https://github.com/amazon-ion/ion-java")
      .apache("https://github.com/amazon-ion/ion-java/blob/master/LICENSE")
      .suppliedByOrganizations("Amazon Ion Team"),
    androidDependency("android-test-plugin-host-device-info-proto"),
    androidDependency(name = "android-test-plugin-host-retention-proto",
                      libraryName = "libstudio.android-test-plugin-host-retention-proto"),
    androidDependency(name = "android-test-plugin-result-listener-gradle-proto",
                      libraryName = "libstudio.android-test-plugin-result-listener-gradle-proto"),
    androidDependency(name = "Android ADB Lib", libraryName = "precompiled-adblib"),
    androidDependency(name = "Android ADB Lib (ddmlib compatibility)", libraryName = "precompiled-adblib.ddmlibcompatibility"),
    androidDependency(name = "Android ADB Lib (tools)", libraryName = "precompiled-adblib.tools"),
    androidDependency(name = "Android AIA Protos", libraryName = "aia-proto"),
    androidDependency(name = "Android Analytics Crash", libraryName = "precompiled-analytics-crash"),
    androidDependency(name = "Android Analytics Protos", libraryName = "studio-analytics-proto"),
    androidDependency(name = "Android Analytics Shared", libraryName = "precompiled-analytics-shared"),
    androidDependency(name = "Android Analytics Tracker", libraryName = "precompiled-analytics-tracker"),
    androidDependency(name = "Android Annotations", libraryName = "precompiled-android-annotations"),
    androidDependency(name = "Android Apk Analyzer", libraryName = "precompiled-analyzer"),
    androidDependency(name = "Android Apk Binary Resources", libraryName = "precompiled-binary-resources"),
    androidDependency(name = "Android Apk Sig", libraryName = "apksig"),
    androidDependency(name = "Android Apk ZLib", libraryName = "apkzlib"),
    androidDependency(name = "Android App Inspector (Background Task, proto)", libraryName = "background-inspector-proto"),
    androidDependency(name = "Android App Inspector (Network, proto)", libraryName = "network_inspector_java_proto"),
    androidDependency(name = "Android Archive Patcher (explainer)", libraryName = "explainer"),
    androidDependency(name = "Android Archive Patcher (generator)", libraryName = "generator"),
    androidDependency(name = "Android Archive Patcher (shared)", libraryName = "shared"),
    androidDependency(name = "Android Baksmali", libraryName = "google-baksmali"),
    androidDependency(name = "Android Build Analysis Result Proto", libraryName = "build-analysis-results-proto"),
    androidDependency(name = "Android Build Analyzer", libraryName = "precompiled-build-analyzer.common"),
    androidDependency(name = "Android Builder Model", libraryName = "precompiled-builder-model"),
    androidDependency(name = "Android Chunkio", libraryName = "precompiled-chunkio"),
    androidDependency(name = "Android Common Library", libraryName = "precompiled-common"),
    // for android-core-proto module library in intellij.android.core
    androidDependency(name = "Android Core Protos", libraryName = "libandroid-core-proto"),
    androidDependency(name = "Android Data Binding Base Library", libraryName = "precompiled-db-baseLibrary"),
    androidDependency(name = "Android Data Binding Base Library Support", libraryName = "precompiled-db-baseLibrarySupport"),
    androidDependency(name = "Android Data Binding Compiler", libraryName = "precompiled-db-compiler"),
    androidDependency(name = "Android Data Binding Compiler Common", libraryName = "precompiled-db-compilerCommon"),
    androidDependency(name = "Android Ddm libapp-processes-proto", libraryName = "libapp-processes-proto"),
    androidDependency(name = "Android Ddm Library", libraryName = "precompiled-ddmlib"),
    androidDependency(name = "Android Deployer Library", libraryName = "precompiled-deployer"),
    androidDependency(name = "Android Deployer Library (libjava_sites)", libraryName = "libjava_sites"),
    androidDependency(name = "Android Device Provisioner Library", libraryName = "precompiled-device-provisioner"),
    androidDependency(name = "Android DEX library", libraryName = "google-dexlib2"),
    androidDependency(name = "Android draw9patch library", libraryName = "precompiled-draw9patch"),
    androidDependency(name = "Android dvlib library", libraryName = "precompiled-dvlib"),
    androidDependency(name = "Android Dynamic Layout Inspector", libraryName = "precompiled-dynamic-layout-inspector.common"),
    androidDependency(name = "Android Emulator gRPC API", libraryName = "emulator-proto"),
    androidDependency(name = "Android Flags", libraryName = "precompiled-flags"),
    LibraryLicense(name = "Android Gradle model", attachedTo = "intellij.android.core", version = "0.4-SNAPSHOT",
                   url = "https://android.googlesource.com/platform/tools/build/+/master/gradle-model/").apache(
      "https://source.android.com/setup/start/licenses")
      .suppliedByOrganizations(Suppliers.GOOGLE),
    LibraryLicense(name = "Android Instant Apps SDK API", url = "https://source.android.com/", libraryName = "instantapps-api",
                   version = LibraryLicense.CUSTOM_REVISION).apache("https://source.android.com/setup/start/licenses")
      .suppliedByOrganizations(Suppliers.GOOGLE),
    androidDependency(name = "Android JdwpPacket", libraryName = "precompiled-jdwppacket"),
    androidDependency(name = "Android JdwpsCache", libraryName = "precompiled-jdwpscache"),
    androidDependency(name = "Android JdwpTracer", libraryName = "precompiled-jdwptracer"),
    androidDependency(name = "Android Jetifier Core", libraryName = "jetifier-core"),
    LibraryLicense(name = "Android Jimfs library", libraryName = "jimfs", url = "https://github.com/google/jimfs")
      .apache("https://github.com/google/jimfs/blob/master/LICENSE"),
    androidDependency(name = "Android Kotlin Multiplatform Models", libraryName = "precompiled-kotlin-multiplatform-models"),
    androidDependency(name = "Android Kotlin Multiplatform Models Proto", libraryName = "kotlin-multiplatform-android-models-proto"),
    androidDependency(name = "Android Layout Api Library", libraryName = "precompiled-layoutlib-api"),
    androidDependency(name = "Android Layout Inspector (Skia Proto)", libraryName = "layoutinspector-skia-proto"),
    androidDependency(name = "Android Layout Inspector (View Proto)", libraryName = "layoutinspector-view-proto"),
    androidDependency(name = "Android Layout Library", libraryName = "layoutlib"),
    LibraryLicense(name = "Android libwebp library", libraryName = "libwebp.jar",
                   url = "https://github.com/webmproject/libwebp",
                   version = LibraryLicense.CUSTOM_REVISION).newBsd("https://github.com/webmproject/libwebp/blob/main/COPYING"),
    androidDependency(name = "Android Lint Api", libraryName = "precompiled-lint-api"),
    androidDependency(name = "Android Lint Checks", libraryName = "precompiled-lint-checks"),
    androidDependency(name = "Android Lint Checks (proto)", libraryName = "liblint-checks-proto"),
    androidDependency(name = "Android Lint Cli", libraryName = "precompiled-lint-cli"),
    androidDependency(name = "Android Lint Model", libraryName = "precompiled-lint-model"),
    androidDependency(name = "Android Manifest Merger", libraryName = "precompiled-manifest-merger"),
    androidDependency(name = "Android Manifest Parser", libraryName = "precompiled-manifest-parser"),
    androidDependency(name = "Android MLKit Common Library", libraryName = "precompiled-mlkit-common"),
    androidDependency(name = "Android ninepatch Library", libraryName = "precompiled-ninepatch"),
    androidDependency(name = "Android Perf-Logger Library", libraryName = "precompiled-perf-logger"),
    androidDependency(name = "Android Perflib Library", libraryName = "precompiled-perflib"),
    androidDependency(name = "Android Pixelprobe Library", libraryName = "precompiled-pixelprobe"),
    androidDependency(name = "Android Process Monitor", libraryName = "precompiled-process-monitor"),
    androidDependency(name = "Android ProfGen", libraryName = "precompiled-profgen"),
    androidDependency(name = "Android Profiler", libraryName = "studio-grpc"),
    androidDependency(name = "Android Repository", libraryName = "precompiled-repository"),
    androidDependency(name = "Android Resource Repository", libraryName = "precompiled-resource-repository"),
    androidDependency(name = "Android Sdk Common", libraryName = "precompiled-sdk-common"),
    androidDependency(name = "Android Sdk Common Gradle", libraryName = "precompiled-sdk-common.gradle"),
    androidDependency(name = "Android Sdk Lib", libraryName = "precompiled-sdklib"),
    androidDependency(name = "Android STracer", libraryName = "precompiled-tracer"),
    androidDependency(name = "Android Studio Driver (proto)", libraryName = "asdriver_proto"),
    androidDependency(name = "Android Threading Agent Callback", libraryName = "precompiled-threading-agent-callback"),
    androidDependency(name = "Android USB Devices", libraryName = "precompiled-usb-devices"),
    androidDependency(name = "Android Version", libraryName = "libversion"),
    androidDependency(name = "Android Wizard Template", libraryName = "precompiled-wizardTemplate.impl"),
    androidDependency(name = "Android Wizard Template Plugin", libraryName = "precompiled-wizardTemplate.plugin"),
    androidDependency(name = "Android Zipflinger", libraryName = "precompiled-zipflinger"),
    androidDependency(name = "AndroidX Test Library", libraryName = "utp-core-proto-jarjar"),
    LibraryLicense(name = "ANTLR 4.5", libraryName = "compilerCommon.antlr.shaded",
                   url = "https://www.antlr.org").newBsd("https://www.antlr.org/license.html"),
    LibraryLicense(name = "ANTLR 4.5 Runtime", libraryName = "compilerCommon.antlr_runtime.shaded",
                   url = "https://www.antlr.org").newBsd("https://www.antlr.org/license.html"),
    LibraryLicense(name = "ANTLR 4.9 Runtime", libraryName = "antlr4-runtime-4.9",
                   url = "https://www.antlr.org").newBsd("https://www.antlr.org/license.html")
      .suppliedByPersons("Terence Parr"),
    LibraryLicense(name = "ap-validation", libraryName = "ap-validation",
                   url = "https://github.com/JetBrains/ap-validation").apache(
      "https://github.com/JetBrains/ap-validation/blob/master/LICENSE"),

    LibraryLicense(libraryName = "apache.logging.log4j.to.slf4j", url = "https://ant.apache.org/")
      .apache("https://logging.apache.org/log4j/log4j-2.2/license.html")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense(name = "Apache Ant", version = "1.9", libraryName = "Ant", url = "https://ant.apache.org/")
      .apache("https://ant.apache.org/license.html"),
    LibraryLicense(name = "Apache Axis", libraryName = "axis-1.4", version = "1.4", url = "https://axis.apache.org/axis/")
      .apache("https://svn.apache.org/viewvc/axis/axis1/java/trunk/LICENSE?view=markup"),
    LibraryLicense(name = "Apache Commons CLI", libraryName = "commons-cli",
                   url = "https://commons.apache.org/proper/commons-cli/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-cli.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"),
    LibraryLicense(name = "Apache Commons Codec", libraryName = "commons-codec", url = "https://commons.apache.org/proper/commons-codec/")
      .apache("https://github.com/apache/commons-codec/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Apache Commons Collections", libraryName = "commons-collections",
                   url = "https://commons.apache.org/proper/commons-collections/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-collections.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"),
    LibraryLicense(name = "Apache Commons Compress", libraryName = "commons-compress",
                   url = "https://commons.apache.org/proper/commons-compress/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-compress.git;a=blob_plain;f=LICENSE.txt;hb=HEAD")
      .suppliedByOrganizations(Suppliers.APACHE),
    LibraryLicense(name = "Apache Commons Discovery", libraryName = "commons-discovery",
                   url = "https://commons.apache.org/dormant/commons-discovery/")
      .apache("https://commons.apache.org/dormant/commons-discovery/license.html")
      .suppliedByPersons(
        "Simone Tripodi", "James Strachan", "Robert Burrell Donkin", "Matthew Hawthorne",
        "Richard Sitze", "Craig R. McClanahan", "Costin Manolache", "Davanum Srinivas", "Rory Winston"
      ),

    LibraryLicense(name = "Apache Commons HTTPClient", libraryName = "http-client-3.1", version = "3.1&nbsp; (with patch by JetBrains)",
                   url = "https://hc.apache.org/httpclient-3.x").apache(),
    LibraryLicense(name = "Apache Commons Imaging (JetBrains's fork)", libraryName = "commons-imaging",
                   url = "https://github.com/JetBrains/intellij-deps-commons-imaging")
      .apache("https://github.com/JetBrains/intellij-deps-commons-imaging/blob/master/LICENSE.txt")
      .forkedFrom(sourceCodeUrl = "https://github.com/apache/commons-imaging",
                  groupId = "org.apache.commons", artifactId = "commons-imaging",
                  revision = "fa201df06edefd329610d210d67caba6802b1211"),
    LibraryLicense(name = "Apache Commons IO", libraryName = "commons-io",
                   url = "https://commons.apache.org/proper/commons-io/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-io.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"),
    LibraryLicense(name = "Apache Commons Lang", libraryName = "commons-lang3",
                   url = "https://commons.apache.org/proper/commons-lang/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-lang.git;a=blob_plain;f=LICENSE.txt;hb=HEAD")
      .suppliedByPersons(
        "Daniel Rall", "Robert Burrell Donkin", "James Carman", "Benedikt Ritter", "Rob Tompkins", "Stephen Colebourne",
        "Henri Yandell", "Steven Caswell", "Gary D. Gregory", "Fredrik Westermarck", "Niall Pemberton", "Matt Benson", "Joerg Schaible",
        "Oliver Heger", "Paul Benedict", "Duncan Jones", "Loic Guibert"
      ),
    LibraryLicense(name = "Apache Commons Logging", libraryName = "commons-logging",
                   url = "https://commons.apache.org/proper/commons-logging/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-logging.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"),
    LibraryLicense(name = "Apache Commons Math", libraryName = "commons-math3",
                   url = "https://commons.apache.org/proper/commons-math/").apache(),
    LibraryLicense(name = "Apache Commons Net", libraryName = "commons-net",
                   url = "https://commons.apache.org/proper/commons-net/")
      .apache("https://gitbox.apache.org/repos/asf?p=commons-net.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"),
    LibraryLicense(name = "Apache Commons Text", libraryName = "commons-text",
                   url = "https://github.com/apache/commons-text")
      .apache("https://github.com/apache/commons-text/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Apache Ivy", libraryName = "org.apache.ivy", url = "https://github.com/apache/ant-ivy")
      .apache("https://github.com/apache/ant-ivy/blob/master/LICENSE")
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "Apache Lucene",
                   libraryName = "lucene-core", url = "https://lucene.apache.org/java",
                   additionalLibraryNames = listOf(
                     "lucene-suggest",
                     "lucene-memory",
                     "lucene-sandbox",
                     "lucene-codecs",
                     "lucene-highlighter",
                     "lucene-queryparser",
                     "lucene-queries",
                     "lucene-analysis-common",
                     "org.apache.lucene:lucene-core:2.4.1"
                   )).apache().suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "Apache Tuweni-Toml", libraryName = "tuweni-toml",
                   url = "https://github.com/apache/incubator-tuweni/tree/main/toml")
      .apache("https://github.com/apache/incubator-tuweni/blob/main/LICENSE"),
    LibraryLicense(name = "ASM (JetBrains's fork)", libraryName = "ASM",
                   url = "https://github.com/JetBrains/intellij-deps-asm")
      .newBsd("https://github.com/JetBrains/intellij-deps-asm/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS)
      .forkedFrom(sourceCodeUrl = "https://gitlab.ow2.org/asm/asm",
                  mavenRepositoryUrl = "https://repo1.maven.org/maven2",
                  groupId = "org.ow2.asm", artifactId = "asm",
                  version = "9.5",
                  authors = "Guillaume Sauthier, Eric Bruneton, Eugene Kuleshov, Remi Forax"),
    LibraryLicense(name = "ASM Tools", libraryName = "asm-tools", url = "https://asm.ow2.io")
      .newBsd("https://asm.ow2.io/license.html"),
    LibraryLicense(name = "AssertJ fluent assertions", libraryName = "assertJ",
                   url = "https://github.com/assertj/assertj-core")
      .apache("https://github.com/assertj/assertj-core/blob/main/LICENSE.txt")
      .suppliedByPersons(
        "Pascal Schumacher", "Joel Costigliola", "Stefano Cordio", "Erhard Pointl", "Christian Rösch",
        "Julien Roy", "Régis Pouiller", "Florent Biville", "Patrick Allain"
      ),
    LibraryLicense(name = "AssertJ Swing", libraryName = "assertj-swing",
                   url = "https://github.com/assertj/assertj-swing")
      .apache("https://github.com/assertj/assertj-swing/blob/main/licence-header.txt")
      .suppliedByPersons("Joel Costigliola", "Joel Costigliola", "Christian Rösch", "Alex Ruiz", "Yvonne Wang", "Ansgar Konermann"),
    LibraryLicense(name = "Atlassian Commonmark", libraryName = "atlassian.commonmark",
                   url = "https://github.com/commonmark/commonmark-java")
      .simplifiedBsd("https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt")
      .suppliedByOrganizations("Atlassian Pty Ltd"),
    LibraryLicense(name = "Automaton", libraryName = "automaton", url = "https://www.brics.dk/automaton/")
      .simplifiedBsd("https://github.com/cs-au-dk/dk.brics.automaton/blob/master/COPYING"),
    LibraryLicense(name = "Bash-Preexec", attachedTo = "intellij.terminal", url = "https://github.com/rcaloras/bash-preexec", version = "0.5.0")
      .mit("https://github.com/rcaloras/bash-preexec/blob/master/LICENSE.md"),
    LibraryLicense(name = "batik", libraryName = "batik-transcoder", url = "https://xmlgraphics.apache.org/batik/")
      .apache("https://xmlgraphics.apache.org/batik/license.html")
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(libraryName = "blockmap",
                   url = "https://github.com/JetBrains/plugin-blockmap-patches")
      .apache("https://github.com/JetBrains/plugin-blockmap-patches/blob/master/LICENSE"),
    LibraryLicense(libraryName = "bouncy-castle-pgp", url = "https://www.bouncycastle.org")
      .mit("https://www.bouncycastle.org/license.html")
      .suppliedByOrganizations("The Legion of the Bouncy Castle Inc."),
    LibraryLicense(libraryName = "bouncy-castle-provider", url = "https://www.bouncycastle.org")
      .mit("https://www.bouncycastle.org/license.html")
      .suppliedByOrganizations("The Legion of the Bouncy Castle Inc."),
    LibraryLicense(name = "Byte Buddy agent", libraryName = "byte-buddy-agent",
                   url = "https://github.com/raphw/byte-buddy")
      .apache("https://github.com/raphw/byte-buddy/blob/master/LICENSE")
      .suppliedByPersons("Rafael Winterhalter"),
    LibraryLicense(name = "caffeine", libraryName = "caffeine",
                   url = "https://github.com/ben-manes/caffeine")
      .apache("https://github.com/ben-manes/caffeine/blob/master/LICENSE")
      .suppliedByPersons("Ben Manes"),
    LibraryLicense(name = "CGLib", libraryName = "CGLIB", url = "https://github.com/cglib/cglib/")
      .apache("https://github.com/cglib/cglib/blob/master/LICENSE")
      .suppliedByPersons("cglib project contributors"),
    LibraryLicense(name = "classgraph", libraryName = "classgraph", license = "codehaus",
                   url = "https://github.com/classgraph/classgraph",
                   licenseUrl = "https://github.com/codehaus/classworlds/blob/master/classworlds/LICENSE.txt"),
    LibraryLicense(name = "Clikt", libraryName = "clikt", url = "https://github.com/ajalt/clikt")
      .apache("https://github.com/ajalt/clikt/blob/master/LICENSE.txt")
      .suppliedByOrganizations("AJ Alt"),
    LibraryLicense(name = "Command Line Interface Parser for Java", libraryName = "cli-parser",
                   url = "https://code.google.com/p/cli-parser/").apache(),
    LibraryLicense(name = "Common Annotations for the JavaTM Platform API", libraryName = "javax.annotation-api",
                   url = "https://github.com/javaee/javax.annotation",
                   license = "CDDL 1.1 / GPL 2.0 + Classpath", licenseUrl = "https://oss.oracle.com/licenses/CDDL+GPL-1.1"),
    // for ui-animation-tooling-internal module library in intellij.android.compose-designer
    LibraryLicense(name = "Compose Animation Tooling", libraryName = "ui-animation-tooling-internal", version = "0.1.0-SNAPSHOT",
                   url = "https://source.android.com/").apache()
      .suppliedByOrganizations(Suppliers.GOOGLE),
    // For ADB wireless QR Code generation
    LibraryLicense(name = "Core barcode encoding/decoding library", url = "https://github.com/zxing/zxing/tree/master/core",
                   libraryName = "zxing-core").apache("https://github.com/zxing/zxing/blob/master/LICENSE")
      .suppliedByOrganizations("ZXing Authors"),
    LibraryLicense(name = "coverage-report", libraryName = "coverage-report",
                   url = "https://github.com/JetBrains/coverage-report")
      .apache("https://github.com/JetBrains/coverage-report/blob/master/LICENSE"),
    LibraryLicense(name = "coverage.py", attachedTo = "intellij.python", version = "4.2.0",
                   url = "https://coverage.readthedocs.io/")
      .apache("https://github.com/nedbat/coveragepy/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Cucumber-Core", libraryName = "cucumber-core-1.2",
                   url = "https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-core")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENCE")
      .suppliedByOrganizations("SmartBear Software"),
    LibraryLicense(name = "Cucumber-Expressions", libraryName = "cucumber-expressions",
                   url = "https://github.com/cucumber/cucumber/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENCE")
      .suppliedByOrganizations("SmartBear Software"),
    LibraryLicense(name = "Cucumber-Groovy", libraryName = "cucumber-groovy", url = "https://github.com/cucumber/cucumber-jvm/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENCE")
      .suppliedByOrganizations("SmartBear Software"),
    LibraryLicense(name = "Cucumber-Java", libraryName = "cucumber-java", url = "https://github.com/cucumber/cucumber-jvm/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENCE")
      .suppliedByOrganizations("SmartBear Software"),
    LibraryLicense(name = "Dart Analysis Server", attachedTo = "intellij.dart",
                   url = "https://github.com/dart-lang/eclipse3", version = LibraryLicense.CUSTOM_REVISION).eplV1(),
    LibraryLicense(name = "Dart VM Service drivers", attachedTo = "intellij.dart",
                   url = "https://github.com/dart-lang/vm_service_drivers",
                   version = LibraryLicense.CUSTOM_REVISION)
      .newBsd("https://github.com/dart-lang/vm_service_drivers/blob/master/LICENSE"),
    LibraryLicense(name = "dbus-java", libraryName = "dbus-java", license = "LGPL",
                   url = "https://github.com/hypfvieh/dbus-java",
                   licenseUrl = "https://github.com/hypfvieh/dbus-java/blob/dbus-java-3.0/LICENSE")
      .suppliedByPersons("David M. <hypfvieh@googlemail.com>"),
    LibraryLicense(name = "DecentXML", libraryName = "decentxml",
                   url = "https://code.google.com/p/decentxml").newBsd(),
    LibraryLicense(name = "docutils", attachedTo = "intellij.python", version = "0.12",
                   url = "https://docutils.sourceforge.io/").simplifiedBsd(),
    LibraryLicense(name = "dotenv-kotlin", libraryName = "io.github.cdimascio.dotenv.kotlin",
                   url = "https://github.com/cdimascio/dotenv-kotlin")
      .apache("https://github.com/cdimascio/dotenv-kotlin/blob/master/LICENSE"),
    LibraryLicense(name = "Eclipse JDT Core", attachedTo = "intellij.platform.jps.build", version = "4.2.1", license = "CPL 1.0",
                   url = "https://www.eclipse.org/jdt/core/index.php"),
    LibraryLicense(name = "Eclipse Layout Kernel", url = "https://www.eclipse.org/elk/", libraryName = "eclipse-layout-kernel").eplV1(),
    LibraryLicense(name = "EditorConfig Java Parser", libraryName = "ec4j-core",
                   url = "https://github.com/ec4j/ec4j").apache()
      .suppliedByPersons("Peter Palaga", "Angelo Zerr"),
    LibraryLicense(name = "emoji-java", libraryName = "com.vdurmont:emoji-java",
                   url = "https://github.com/vdurmont/emoji-java")
      .mit("https://github.com/vdurmont/emoji-java/blob/master/LICENSE.md")
      .suppliedByPersons("Vincent DURMONT"),
    LibraryLicense(name = "entities",
                   url = "https://github.com/fb55/entities", attachedTo = "intellij.vuejs",
                   version = LibraryLicense.CUSTOM_REVISION)
      .simplifiedBsd("https://github.com/fb55/entities/blob/master/LICENSE"),
    LibraryLicense(name = "epydoc", attachedTo = "intellij.python", version = "3.0.1",
                   url = "https://epydoc.sourceforge.net/").mit(),
    LibraryLicense(name = "fastutil", libraryName = "fastutil-min", url = "https://github.com/vigna/fastutil")
      .apache("https://github.com/vigna/fastutil/blob/master/LICENSE-2.0")
      .suppliedByPersons("Sebastiano Vigna"),
    ffmpegLibraryLicense("ffmpeg"),
    ffmpegLibraryLicense("ffmpeg-javacpp"),
    ffmpegLibraryLicense("ffmpeg-linux-x64"),
    ffmpegLibraryLicense("ffmpeg-macos-aarch64"),
    ffmpegLibraryLicense("ffmpeg-macos-x64"),
    ffmpegLibraryLicense("ffmpeg-windows-x64"),
    LibraryLicense(name = "FiraCode", attachedTo = "intellij.platform.resources", version = "1.206", license = "OFL",
                   url = "https://github.com/tonsky/FiraCode", licenseUrl = "https://github.com/tonsky/FiraCode/blob/master/LICENSE"),
    // for flatbuffers-java module library in android.sdktools.mlkit-common
    LibraryLicense(name = "FlatBuffers Java API", libraryName = "flatbuffers-java",
                   url = "https://google.github.io/flatbuffers/")
      .apache("https://github.com/google/flatbuffers/blob/master/LICENSE"),
    LibraryLicense(name = "FreeMarker", attachedTo = "intellij.java.coverage", version = "2.3.30",
                   url = "https://freemarker.apache.org")
      .apache("https://freemarker.apache.org/docs/app_license.html"),
    LibraryLicense(name = "gauge-java", libraryName = "com.thoughtworks.gauge:gauge-java",
                   url = "https://github.com/getgauge/gauge-java/")
      .apache("https://github.com/getgauge/gauge-java/raw/master/LICENSE.txt"),
    LibraryLicense(name = "Gherkin", libraryName = "gherkin",
                   url = "https://github.com/cucumber/gherkin/tree/main")
      .mit("https://github.com/cucumber/gherkin/blob/main/LICENSE")
      .suppliedByOrganizations("Cucumber Ltd"),
    LibraryLicense(name = "Gherkin keywords", attachedTo = "intellij.gherkin", version = "2.12.2",
                   url = "https://github.com/cucumber/gherkin/tree/main")
      .mit("https://github.com/cucumber/gherkin/blob/main/LICENSE")
      .suppliedByOrganizations("Cucumber Ltd"),
    LibraryLicense(url = "https://github.com/oshi/oshi", libraryName = "github.oshi.core").mit(
      "https://github.com/oshi/oshi/blob/master/LICENSE")
      .suppliedByOrganizations("The OSHI Project Contributors"),
    LibraryLicense(name = "Google Auto Common Utilities", libraryName = "auto-common",
                   url = "https://github.com/google/auto/tree/master/common")
      .apache("https://github.com/google/auto/blob/master/LICENSE"),
    LibraryLicense(libraryName = "Gradle", url = "https://gradle.org/", licenseUrl = "https://gradle.org/license")
      .apache("https://github.com/gradle/gradle/blob/master/LICENSE")
      .suppliedByOrganizations("Gradle Inc."),
    LibraryLicense(name = "GraphQL Java", url = "https://github.com/graphql-java/graphql-java",
                   attachedTo = "intellij.graphql", version = LibraryLicense.CUSTOM_REVISION)
      .mit("https://github.com/graphql-java/graphql-java/blob/master/LICENSE.md"),
    LibraryLicense(name = "GraphQL Java Dataloader", libraryName = "graphql.java.dataloader",
                   url = "https://github.com/graphql-java/java-dataloader")
      .apache("https://github.com/graphql-java/java-dataloader/blob/master/LICENSE"),
    LibraryLicense(name = "Grazie AI", libraryName = "ai.grazie.spell.gec.engine.local",
                   url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/",
                   additionalLibraryNames = listOf("ai.grazie.nlp.patterns",
                                                   "ai.grazie.nlp.phonetics",
                                                   "ai.grazie.nlp.common",
                                                   "ai.grazie.nlp.langs",
                                                   "ai.grazie.nlp.similarity",
                                                   "ai.grazie.nlp.detect",
                                                   "ai.grazie.nlp.stemmer",
                                                   "ai.grazie.nlp.tokenizer",
                                                   "ai.grazie.utils.common",
                                                   "ai.grazie.utils.json",
                                                   "ai.grazie.utils.lucene.lt.compatibility",
                                                   "ai.grazie.model.bdg.jvm",
                                                   "ai.grazie.model.common",
                                                   "ai.grazie.model.gec",
                                                   "ai.grazie.model.text",
                                                   "ai.grazie.spell.hunspell.en",
                                                   "ai.grazie.emb",
                                                   "ai.grazie.model.nlp.encoder",
                                                   "ai.grazie.utils.ki",
                                                   "ai.grazie.nlp.encoder",
                                                   "ai.grazie.nlp.encoder.bert.uncased")).apache()
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Groovy", libraryName = "org.codehaus.groovy:groovy", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),
    LibraryLicense(name = "Groovy Ant", libraryName = "org.codehaus.groovy:groovy-ant", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),
    LibraryLicense(name = "Groovy JSON", libraryName = "org.codehaus.groovy:groovy-json", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),
    LibraryLicense(name = "Groovy JSR-223", libraryName = "org.codehaus.groovy:groovy-jsr223", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),
    LibraryLicense(name = "Groovy Templates", libraryName = "org.codehaus.groovy:groovy-templates",
                   url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),
    LibraryLicense(name = "Groovy XML", libraryName = "org.codehaus.groovy:groovy-xml",
                   url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense(libraryName = "grpc-inprocess", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),
    LibraryLicense(name = "gRPC Kotlin: Stub", libraryName = "grpc-kotlin-stub", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),
    LibraryLicense(name = "gRPC: Core", libraryName = "grpc-core", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),
    LibraryLicense(name = "gRPC: Netty Shaded", libraryName = "grpc-netty-shaded", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),
    LibraryLicense(name = "gRPC: Protobuf", libraryName = "grpc-protobuf", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),
    LibraryLicense(name = "gRPC: Stub", libraryName = "grpc-stub", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense(name = "Gson", libraryName = "gson", url = "https://github.com/google/gson")
      .apache("https://github.com/google/gson/blob/master/LICENSE"),
    LibraryLicense(libraryName = "Guava", url = "https://github.com/google/guava")
      .apache("https://github.com/google/guava/raw/master/LICENSE"),
    LibraryLicense(name = "Hamcrest", libraryName = "hamcrest", url = "https://hamcrest.org/")
      .newBsd("https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE.txt")
      .suppliedByPersons("Joe Walnes", "Nat Pryce", "Steve Freeman"),
    LibraryLicense(libraryName = "hash4j", url = "https://github.com/dynatrace-oss/hash4j")
      .apache("https://github.com/dynatrace-oss/hash4j/blob/main/LICENSE"),

    LibraryLicense(name = "HDR Histogram", libraryName = "HdrHistogram", license = "CC0 1.0 Universal",
                   url = "https://github.com/HdrHistogram/HdrHistogram",
                   licenseUrl = "https://github.com/HdrHistogram/HdrHistogram/blob/master/LICENSE.txt")
      .suppliedByPersons("Gil Tene"),
    LibraryLicense(name = "hppc", url = "https://github.com/carrotsearch/hppc", libraryName = "com.carrotsearch:hppc")
      .apache("https://github.com/carrotsearch/hppc/blob/master/LICENSE.txt")
      .suppliedByPersons("Stanisław Osiński", "Dawid Weiss", "Bruno Roustant"),
    LibraryLicense(name = "htmlparser2",
                   url = "https://github.com/fb55/htmlparser2", attachedTo = "intellij.vuejs",
                   version = LibraryLicense.CUSTOM_REVISION)
      .mit("https://github.com/fb55/htmlparser2/blob/master/LICENSE"),
    LibraryLicense(name = "HttpComponents HttpClient", libraryName = "http-client",
                   url = "https://hc.apache.org/httpcomponents-client-ga/index.html").apache()
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "HttpComponents HttpClient Fluent API", libraryName = "fluent-hc",
                   url = "https://hc.apache.org/httpcomponents-client-ga/index.html").apache()
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "ICU4J", libraryName = "icu4j", license = "Unicode",
                   url = "https://icu.unicode.org/", licenseUrl = "https://www.unicode.org/copyright.html"),
    LibraryLicense(name = "imgscalr", libraryName = "imgscalr", url = "https://github.com/thebuzzmedia/imgscalr")
      .apache("https://github.com/rkalla/imgscalr/blob/master/LICENSE"),
    LibraryLicense(name = "Inconsolata", attachedTo = "intellij.platform.resources", version = "001.010", license = "OFL",
                   url = "https://github.com/google/fonts/tree/main/ofl/inconsolata",
                   licenseUrl = "https://github.com/google/fonts/blob/master/ofl/inconsolata/OFL.txt"),
    LibraryLicense(name = "Incremental DOM", attachedTo = "intellij.markdown", version = "0.7.0",
                   url = "https://github.com/google/incremental-dom")
      .apache("https://github.com/google/incremental-dom/blob/master/LICENSE"),
    LibraryLicense(name = "indriya", libraryName = "tech.units:indriya:1.3",
                   url = "https://github.com/unitsofmeasurement/indriya",
                   licenseUrl = "https://github.com/unitsofmeasurement/indriya/blob/master/LICENSE")
      .newBsd("https://github.com/unitsofmeasurement/indriya/blob/master/LICENSE")
      .suppliedByPersons(
        "Jean-Marie Dautelle", "Werner Keil", "Otávio Gonçalves de Santana",
        "Martin Desruisseaux", "Thodoris Bais", "Daniel Dias",
        "Jacob Glickman", "Magesh Kasthuri"
      ),
    LibraryLicense(name = "ini4j (JetBrains's fork)", libraryName = "ini4j",
                   url = "https://github.com/JetBrains/intellij-deps-ini4j")
      .apache("https://github.com/JetBrains/intellij-deps-ini4j/blob/master/LICENSE.txt")
      .forkedFrom(sourceCodeUrl = "https://sourceforge.net/projects/ini4j",
                  mavenRepositoryUrl = "https://repo1.maven.org/maven2",
                  groupId = "org.ini4j", artifactId = "ini4j",
                  version = "0.5.4",
                  authors = "Ivan Szkiba"),
    androidDependency(name = "Instant run protos", libraryName = "deploy_java_proto"),
    androidDependency(name = "Instant run version", libraryName = "libjava_version"),
    LibraryLicense(name = "intellij-markdown", libraryName = "jetbrains.markdown",
                   url = "https://github.com/JetBrains/markdown")
      .apache("https://github.com/JetBrains/markdown/blob/master/LICENSE"),
    LibraryLicense(name = "IntelliJ IDEA Code Coverage Agent", libraryName = "intellij-coverage",
                   url = "https://github.com/jetbrains/intellij-coverage")
      .apache("https://github.com/JetBrains/intellij-coverage/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "IntelliJ IDEA Test Discovery Agent", libraryName = "intellij-test-discovery",
                   url = "https://github.com/JetBrains/intellij-coverage/tree/master/test-discovery")
      .apache("https://github.com/JetBrains/intellij-coverage/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "ISO RELAX", libraryName = "isorelax", url = "https://sourceforge.net/projects/iso-relax/").mit()
      .suppliedByPersons("Asami Tomoharu", "Murata Makoto", "Kohsuke Kawaguchi"),

    LibraryLicense(name = "Jackson", libraryName = "jackson", url = "https://github.com/FasterXML/jackson")
      .apache("https://github.com/FasterXML/jackson-core/blob/2.14/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),
    LibraryLicense(name = "jackson-jr-objects", libraryName = "jackson-jr-objects",
                   url = "https://github.com/FasterXML/jackson-jr")
      .apache("https://github.com/FasterXML/jackson-jr/blob/2.16/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),
    LibraryLicense(name = "Jackson Databind", libraryName = "jackson-databind",
                   url = "https://github.com/FasterXML/jackson-databind")
      .apache("https://github.com/FasterXML/jackson-databind/blob/2.16/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense(name = "Jackson Dataformat CBOR", libraryName = "jackson-dataformat-cbor",
                   url = "https://github.com/FasterXML/jackson-dataformats-binary")
      .apache("https://github.com/FasterXML/jackson-dataformats-binary/blob/2.14/pom.xml")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense(name = "Jackson Dataformat YAML", libraryName = "jackson-dataformat-yaml",
                   url = "https://github.com/FasterXML/jackson-dataformats-text")
      .apache("https://github.com/FasterXML/jackson-dataformats-text/blob/2.16/pom.xml")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),
    LibraryLicense(name = "Jackson Module Kotlin", libraryName = "jackson-module-kotlin",
                   url = "https://github.com/FasterXML/jackson-module-kotlin")
      .apache("https://github.com/FasterXML/jackson-module-kotlin/blob/2.16/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown", "Jayson Minard",
                         "Drew Stephens", "Vyacheslav Artemyev", "Dmitry Spikhalskiy"),

    LibraryLicense(name = "JaCoCo", libraryName = "JaCoCo", url = "https://www.eclemma.org/jacoco/").eplV1(),
    LibraryLicense(name = "Jakarta ORO", libraryName = "OroMatcher",
                   url = "https://jakarta.apache.org/oro/")
      .apache("https://svn.apache.org/repos/asf/jakarta/oro/trunk/LICENSE")
      .suppliedByPersons(
        "Daniel Savarese",
        "Jon S. Stevens",
        "Takashi Okamoto",
        "Mark Murphy",
        "Michael Davey",
        "Harald Kuhn",
      ),
    LibraryLicense(name = "Jarchivelib", libraryName = "rauschig.jarchivelib",
                   url = "https://github.com/thrau/jarchivelib")
      .apache("https://github.com/thrau/jarchivelib/blob/master/LICENSE"),
    LibraryLicense(libraryName = "Java Compatibility", license = "GPL 2.0 + Classpath",
                   url = "https://github.com/JetBrains/intellij-deps-java-compatibility",
                   licenseUrl = "https://github.com/JetBrains/intellij-deps-java-compatibility/raw/master/LICENSE"),

    LibraryLicense(name = "Java Poet", libraryName = "javapoet",
                   url = "https://github.com/square/javapoet")
      .apache("https://github.com/square/javapoet/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Java Server Pages (JSP) for Visual Studio Code", attachedTo = "intellij.textmate", version = "0.0.3",
                   url = "https://github.com/pthorsson/vscode-jsp",
                   licenseUrl = "https://github.com/pthorsson/vscode-jsp/blob/master/LICENSE").mit(),
    LibraryLicense(name = "Java Simple Serial Connector", libraryName = "io.github.java.native.jssc",
                   url = "https://github.com/java-native/jssc", license = "LGPL 3.0",
                   licenseUrl = "https://github.com/java-native/jssc/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Java String Similarity", libraryName = "java-string-similarity",
                   url = "https://github.com/tdebatty/java-string-similarity")
      .mit("https://github.com/tdebatty/java-string-similarity/blob/master/LICENSE.md")
      .suppliedByPersons("Thibault Debatty"),
    LibraryLicense(name = "JavaBeans Activation Framework", libraryName = "javax.activation",
                   url = "https://github.com/javaee/activation",
                   license = "CDDL 1.1 / GPL 2.0 + Classpath",
                   licenseUrl = "https://github.com/javaee/activation/blob/master/LICENSE.txt")
      .suppliedByPersons("Bill Shannon"),
    ffmpegLibraryLicense("javacpp-linux-x64"),
    ffmpegLibraryLicense("javacpp-macos-aarch64"),
    ffmpegLibraryLicense("javacpp-macos-x64"),
    ffmpegLibraryLicense("javacpp-windows-x64"),
    LibraryLicense(name = "javaslang", libraryName = "javaslang", url = "https://javaslang.io/").apache()
      .suppliedByPersons("Daniel Dietrich"),
    LibraryLicense(name = "javawriter", attachedTo = "intellij.android.core",
                   url = "https://github.com/square/javawriter",
                   version = LibraryLicense.CUSTOM_REVISION).apache(),
    LibraryLicense(name = "javax inject", libraryName = "javax-inject",
                   url = "https://code.google.com/p/atinject/").apache()
      .suppliedByOrganizations(Suppliers.GOOGLE),
    LibraryLicense(name = "JAXB (Java Architecture for XML Binding) API", libraryName = "jaxb-api",
                   url = "https://github.com/javaee/jaxb-spec",
                   license = "CDDL 1.1 / GPL 2.0 + Classpath", licenseUrl = "https://oss.oracle.com/licenses/CDDL+GPL-1.1")
      .suppliedByPersons("Roman Grigoriadi", "Martin Grebac", "Iaroslav Savytskyi"),
    LibraryLicense(name = "JAXB (JSR 222) Reference Implementation", libraryName = "jaxb-runtime",
                   url = "https://github.com/javaee/jaxb-v2",
                   license = "CDDL 1.1 / GPL 2.0 + Classpath", licenseUrl = "https://oss.oracle.com/licenses/CDDL+GPL-1.1")
      .suppliedByOrganizations("Eclipse Foundation"),
    LibraryLicense(libraryName = "Jaxen", url = "https://github.com/jaxen-xpath/jaxen")
      .newBsd("https://github.com/jaxen-xpath/jaxen/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Jayway JsonPath", libraryName = "jsonpath",
                   url = "https://github.com/json-path/JsonPath")
      .apache("https://github.com/json-path/JsonPath/blob/master/LICENSE"),
    LibraryLicense(libraryName = "jb-jdi", license = "GPL 2.0 + Classpath", url = "https://github.com/JetBrains/intellij-deps-jdi",
                   licenseUrl = "https://github.com/JetBrains/intellij-deps-jdi/raw/master/LICENSE.txt"),
    LibraryLicense(name = "JCEF", libraryName = "jcef", license = "BSD 3-Clause",
                   licenseUrl = "https://bitbucket.org/chromiumembedded/java-cef/src/master/LICENSE.txt",
                   url = "https://bitbucket.org/chromiumembedded/java-cef")
      .suppliedByPersons("Marshall A. Greenblatt"),
    LibraryLicense(name = "JCIP Annotations", libraryName = "jcip", license = "Creative Commons Attribution License",
                   url = "https://jcip.net", licenseUrl = "https://creativecommons.org/licenses/by/2.5")
      .suppliedByPersons("Tim Peierls", "Brian Goetz"),
    LibraryLicense(name = "JCodings", libraryName = "joni", transitiveDependency = true, version = "1.0.55",
                   url = "https://github.com/jruby/jcodings")
      .mit("https://github.com/jruby/jcodings/blob/master/LICENSE.txt"),
    LibraryLicense(name = "JDOM (JetBrains's fork)", version = "2", attachedTo = "intellij.platform.util.jdom",
                   url = "https://github.com/JetBrains/intellij-deps-jdom/",
                   licenseUrl = "https://github.com/JetBrains/intellij-deps-jdom/blob/master/LICENSE.txt")
      .forkedFrom(sourceCodeUrl = "https://github.com/hunterhacker/jdom",
                  mavenRepositoryUrl = "https://repo1.maven.org/maven2",
                  groupId = "org.jdom", artifactId = "jdom2",
                  version = "2.0.6"),
    LibraryLicense(libraryName = "jediterm-core", license = "LGPL 3",
                   url = "https://github.com/JetBrains/jediterm",
                   licenseUrl = "https://github.com/JetBrains/jediterm/blob/master/LICENSE-LGPLv3.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "jediterm-ui", license = "LGPL 3",
                   url = "https://github.com/JetBrains/jediterm",
                   licenseUrl = "https://github.com/JetBrains/jediterm/blob/master/LICENSE-LGPLv3.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "jetbrains.kotlinx.metadata.jvm", libraryName = "jetbrains.kotlinx.metadata.jvm",
                   url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "JetBrains Annotations", libraryName = "jetbrains-annotations",
                   url = "https://github.com/JetBrains/java-annotations")
      .apache("https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt"),
    LibraryLicense(name = "JetBrains Annotations for Java 5", libraryName = "jetbrains-annotations-java5",
                   url = "https://github.com/JetBrains/java-annotations")
      .apache("https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt"),
    LibraryLicense(name = "JetBrains Runtime", attachedTo = "intellij.platform.ide.impl", version = "11",
                   license = "GNU General Public License, version 2, with the Classpath Exception",
                   url = "https://github.com/JetBrains/JetBrainsRuntime",
                   licenseUrl = "https://github.com/JetBrains/JetBrainsRuntime/blob/master/LICENSE"),
    LibraryLicense(name = "JetBrains Runtime API", libraryName = "jbr-api",
                   url = "https://github.com/JetBrains/JetBrainsRuntime").apache(),
    LibraryLicense(name = "jetCheck", libraryName = "jetCheck", url = "https://github.com/JetBrains/jetCheck")
      .apache("https://github.com/JetBrains/jetCheck/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "JGit (Settings Sync and SettingsRepo)", libraryName = "jetbrains.intellij.deps.eclipse.jgit",
                   license = "Eclipse Distribution License 1.0",
                   licenseUrl = "https://www.eclipse.org/org/documents/edl-v10.php", url = "https://www.eclipse.org/jgit/")
      .suppliedByOrganizations("Eclipse Foundation"),
    LibraryLicense(name = "JGoodies Common", libraryName = "jgoodies-common",
                   url = "https://www.jgoodies.com/freeware/libraries/looks/").simplifiedBsd(),
    LibraryLicense(name = "JGoodies Forms", libraryName = "jgoodies-forms",
                   url = "https://www.jgoodies.com/freeware/libraries/forms/").simplifiedBsd()
      .suppliedByOrganizations("JGoodies Software GmbH"),
    LibraryLicense(name = "Jing", libraryName = "jing", url = "https://relaxng.org/jclark/jing.html")
      .newBsd("https://opensource.org/license/bsd-3-clause/")
      .suppliedByOrganizations("Thai Open Source Software Center Ltd"),
    LibraryLicense(name = "JNA", libraryName = "jna", license = "LGPL 2.1",
                   url = "https://github.com/java-native-access/jna",
                   licenseUrl = "https://www.opensource.org/licenses/lgpl-2.1.php"),
    LibraryLicense(name = "Joni", libraryName = "joni", url = "https://github.com/jruby/joni")
      .mit("https://github.com/jruby/joni/blob/master/LICENSE"),
    LibraryLicense(name = "jps-javac-extension", libraryName = "jps-javac-extension",
                   url = "https://github.com/JetBrains/jps-javac-extension/")
      .apache("https://github.com/JetBrains/jps-javac-extension/blob/master/LICENSE.txt"),
    LibraryLicense(libraryName = "JSch", url = "http://www.jcraft.com/jsch/").newBsd("http://www.jcraft.com/jsch/LICENSE.txt")
      .suppliedByPersons("Atsuhiko Yamanaka"),
    LibraryLicense(name = "jsch", libraryName = "eclipse.jgit.ssh.jsch", url = "http://www.jcraft.com/jsch/")
      .newBsd("http://www.jcraft.com/jsch/LICENSE.txt")
      .suppliedByPersons("Atsuhiko Yamanaka"),
    LibraryLicense(libraryName = "jsch-agent-proxy", url = "https://github.com/ymnk/jsch-agent-proxy")
      .newBsd("https://github.com/ymnk/jsch-agent-proxy/blob/master/LICENSE.txt")
      .suppliedByPersons("Atsuhiko Yamanaka"),
    LibraryLicense(name = "JSON", libraryName = "json.jar", license = "JSON License", licenseUrl = "https://www.json.org/license.html",
                   url = "https://www.json.org/", version = LibraryLicense.CUSTOM_REVISION),
    LibraryLicense(name = "JSON in Java", libraryName = "org.json:json:20170516", license = "JSON License",
                   licenseUrl = "https://www.json.org/license.html", url = "https://github.com/stleary/JSON-java"),
    LibraryLicense(name = "JSON Schema (schema.json)", attachedTo = "intellij.json", version = "draft-04",
                   url = "https://json-schema.org/draft-04/schema#").simplifiedBsd(),
    LibraryLicense(name = "JSON Schema (schema06.json)", attachedTo = "intellij.json", version = "draft-06",
                   url = "https://json-schema.org/draft-06/schema#").simplifiedBsd(),
    LibraryLicense(name = "JSON Schema (schema07.json)", attachedTo = "intellij.json", version = "draft-07",
                   url = "https://json-schema.org/draft-07/schema#").simplifiedBsd(),
    LibraryLicense(libraryName = "jsoup", url = "https://jsoup.org").mit("https://jsoup.org/license"),
    LibraryLicense(libraryName = "jsr305", url = "https://code.google.com/p/jsr-305/")
      .newBsd("https://opensource.org/license/bsd-3-clause/")
      .suppliedByOrganizations("JSR305 expert group"),
    LibraryLicense(libraryName = "jsvg", url = "https://github.com/weisJ/jsvg").mit("https://github.com/weisJ/jsvg/blob/master/LICENSE")
      .suppliedByPersons("Jannis Weis"),
    LibraryLicense(libraryName = "JUnit3", license = "CPL 1.0", url = "https://junit.org/")
      .suppliedByPersons("Marc Philipp", "David Saff", "Kevin Cooney", "Stefan Birkner"),
    LibraryLicense(libraryName = "JUnit4", url = "https://junit.org/").eplV1()
      .suppliedByPersons("Marc Philipp", "David Saff", "Kevin Cooney", "Stefan Birkner"),
    LibraryLicense(name = "JUnit5", libraryName = "JUnit5", url = "https://junit.org/junit5/").eplV2(),
    LibraryLicense(name = "JUnit5Jupiter", libraryName = "JUnit5Jupiter", url = "https://junit.org/junit5/").eplV2(),
    LibraryLicense(name = "JUnit5Launcher", libraryName = "JUnit5Launcher", url = "https://junit.org/junit5/").eplV2(),
    LibraryLicense(name = "JUnit5Vintage", libraryName = "JUnit5Vintage", url = "https://junit.org/junit5/").eplV2(),
    LibraryLicense(name = "Juniversalchardet", libraryName = "juniversalchardet",
                   url = "https://code.google.com/archive/p/juniversalchardet",
                   license = "MPL 1.1", licenseUrl = "https://www.mozilla.org/MPL/MPL-1.1.html"),
    LibraryLicense(libraryName = "jzlib", url = "http://www.jcraft.com/jzlib/").newBsd("https://github.com/ymnk/jzlib/raw/master/LICENSE.txt"),
    LibraryLicense(name = "KInference",
                   libraryName = "kinference.core.jvm",
                   url = "https://packages.jetbrains.team/maven/p/ki/maven",
                   additionalLibraryNames = listOf(
                     "kinference.api.jvm",
                     "kinference.common.utils.jvm",
                     "kinference.ir.jvm",
                     "kinference.logger.jvm",
                     "kinference.model.profiler.jvm",
                     "kinference.ndarray.api.jvm",
                     "kinference.ndarray.core.jvm",
                     "kinference.primitives.annotations.jvm",
                     "kinference.serializer.protobuf.jvm",
                     "kinference.serializer.tiled.jvm",
                     "kinference.core.jvm",
                     "kinference.utils",
                   )).apache("https://github.com/JetBrains-Research/kinference/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kodein-DI", libraryName = "kodein-di-jvm", url = "https://github.com/kosi-libs/Kodein")
      .mit("https://github.com/kosi-libs/Kodein/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Kotlin Coroutines for Guava", libraryName = "kotlinx-coroutines-guava",
                   url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin Coroutines for JDK 8", libraryName = "kotlinx-coroutines-core",
                   url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin Coroutines for Slf4j", libraryName = "kotlinx-coroutines-slf4j",
                   url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(name = "Kotlin multiplatform / multi-format serialization",
                   libraryName = "kotlinx-serialization-core",
                   url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin multiplatform / multi-format serialization",
                   libraryName = "kotlinx-serialization-json",
                   url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin multiplatform / multi-format serialization",
                   libraryName = "kotlinx-serialization-protobuf",
                   url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin multiplatform / multi-format serialization",
                   libraryName = "kotlinx-serialization-cbor",
                   url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(name = "Kotlin reflection library",
                   libraryName = "kotlin-reflect",
                   url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kotlin Standard Library",
                   libraryName = "kotlin-stdlib",
                   url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "kotlinx-datetime-jvm",
                   libraryName = "kotlinx-datetime-jvm",
                   url = "https://github.com/Kotlin/kotlinx-datetime")
      .apache("https://github.com/Kotlin/kotlinx-datetime/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "kotlinx.html", libraryName = "kotlinx-html-jvm",
                   url = "https://github.com/Kotlin/kotlinx.html")
      .apache("https://github.com/Kotlin/kotlinx.html/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "Kryo5", libraryName = "Kryo5",
                   url = "https://github.com/EsotericSoftware/kryo")
      .newBsd("https://github.com/EsotericSoftware/kryo/blob/master/LICENSE.md")
      .suppliedByPersons("Nathan Sweet"),
    LibraryLicense(libraryName = "ktor-client-auth",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-client-cio",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-client-content-negotiation",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-client-encoding",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-client-jvm",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-client-logging",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-serialization-gson",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(libraryName = "ktor-serialization-kotlinx-json",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "ktor.io TLS", libraryName = "ktor-network-tls",
                   url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "kXML2", libraryName = "kxml2", url = "https://sourceforge.net/projects/kxml/").simplifiedBsd(),
    LibraryLicense(name = "Language Tool", libraryName = "org.languagetool:languagetool-core",
                   url = "https://github.com/languagetool-org/languagetool",
                   license = "LGPL 2.1",
                   licenseUrl = "https://github.com/languagetool-org/languagetool/blob/master/COPYING.txt")
      .suppliedByPersons("Daniel Naber", "Marcin Miłkowski"),
    LibraryLicense(name = "Language Tool (English)", libraryName = "org.languagetool:language-en",
                   url = "https://github.com/languagetool-org/languagetool",
                   license = "LGPL 2.1",
                   licenseUrl = "https://github.com/languagetool-org/languagetool/blob/master/COPYING.txt")
      .suppliedByPersons("Daniel Naber", "Marcin Miłkowski"),
    LibraryLicense(name = "Log4j", libraryName = "Log4J",
                   url = "https://www.slf4j.org/legacy.html#log4j-over-slf4j").apache()
      .suppliedByOrganizations("QOS.ch Sarl"),
    LibraryLicense(name = "lz4-java", libraryName = "lz4-java",
                   url = "https://github.com/lz4/lz4-java")
      .apache("https://github.com/lz4/lz4-java/blob/master/LICENSE.txt"),
    LibraryLicense(name = "MathJax", attachedTo = "intellij.python", version = "2.6.1",
                   url = "https://github.com/mathjax/MathJax",
                   licenseUrl = "https://github.com/mathjax/MathJax/blob/master/LICENSE").apache(),


    LibraryLicense(name = "Maven archetype common", libraryName = "apache.maven.archetype.common:3.2.1",
                   url = "https://maven.apache.org/archetype/archetype-common/index.html")
      .apache("https://github.com/apache/maven-archetype"),

    LibraryLicense(name = "Maven core", libraryName = "apache.maven.core:3.8.3",
                   url = "https://maven.apache.org/ref/3.8.6/maven-core/")
      .apache("https://github.com/apache/maven/blob/master/LICENSE"),

    LibraryLicense(name = "Maven indexer", libraryName = "jetbrains.idea.maven.indexer.api.rt",
                   url = "https://maven.apache.org/maven-indexer/indexer-core/index.html")
      .apache("https://github.com/apache/maven-indexer")
      .suppliedByOrganizations("The Apache Software Foundation"),

    LibraryLicense(name = "Maven Resolver Provider",
                   url = "https://maven.apache.org/ref/3.6.1/maven-resolver-provider/", libraryName = "maven-resolver-provider",
                   additionalLibraryNames = listOf("org.apache.maven.resolver:maven-resolver-connector-basic",
                                                   "org.apache.maven.resolver:maven-resolver-transport-http",
                                                   "org.apache.maven.resolver:maven-resolver-transport-file")).apache()
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "Maven wagon provider api", libraryName = "apache.maven.wagon.provider.api:3.5.2",
                   url = "https://maven.apache.org/wagon/wagon-provider-api/index.html")
      .apache("https://github.com/apache/maven-wagon")
      .suppliedByOrganizations("The Apache Software Foundation"),

    LibraryLicense(name = "Maven Wrapper", libraryName = "io.takari.maven.wrapper",
                   url = "https://github.com/takari/maven-wrapper").apache(),
    LibraryLicense(name = "Maven3", attachedTo = "intellij.maven.server.m3.common",
                   additionalLibraryNames = listOf("org.apache.maven.shared:maven-dependency-tree:1.2",
                                                   "org.apache.maven.archetype:archetype-common:2.2"),
                   version = "3.6.1", url = "https://maven.apache.org/").apache(),
    LibraryLicense(name = "Memory File System", libraryName = "memoryfilesystem",
                   url = "https://github.com/marschall/memoryfilesystem")
      .mit("https://github.com/marschall/memoryfilesystem#faq"),
    LibraryLicense(name = "mercurial_prompthooks", attachedTo = "intellij.vcs.hg", version = LibraryLicense.CUSTOM_REVISION,
                   license = "GPLv2 (used as hg extension called from hg executable)",
                   url = "https://github.com/willemv/mercurial_prompthooks",
                   licenseUrl = "https://github.com/willemv/mercurial_prompthooks/blob/master/LICENSE.txt"),
    LibraryLicense(libraryName = "microba", url = "https://microba.sourceforge.net/",
                   licenseUrl = "https://microba.sourceforge.net/license.txt").newBsd()
      .suppliedByPersons("Michael Baranov"),
    LibraryLicense(name = "MigLayout", libraryName = "miglayout-swing",
                   url = "https://www.miglayout.com/", licenseUrl = "https://www.miglayout.com/mavensite/license.html").newBsd()
      .suppliedByOrganizations("MiG InfoCom AB"),
    LibraryLicense(name = "morfologik-fsa", libraryName = "org.carrot2:morfologik-fsa",
                   url = "https://github.com/morfologik/morfologik-stemming").simplifiedBsd()
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),
    LibraryLicense(name = "morfologik-fsa-builders", libraryName = "org.carrot2:morfologik-fsa-builders",
                   url = "https://github.com/morfologik/morfologik-stemming").simplifiedBsd()
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),
    LibraryLicense(name = "morfologik-speller", libraryName = "org.carrot2:morfologik-speller",
                   url = "https://github.com/morfologik/morfologik-stemming").simplifiedBsd()
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),
    LibraryLicense(name = "morfologik-stemming", libraryName = "org.carrot2:morfologik-stemming",
                   url = "https://github.com/morfologik/morfologik-stemming").simplifiedBsd()
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),
    LibraryLicense(libraryName = "mvstore", url = "https://github.com/h2database/h2database")
      .eplV1("https://github.com/h2database/h2database/blob/master/LICENSE.txt"),

    LibraryLicense(name = "NanoXML", license = "zlib/libpng", version = "2.2.3",
                   url = "https://central.sonatype.com/artifact/be.cyberelf.nanoxml/nanoxml/2.2.3",
                   licenseUrl = "https://github.com/saulhidalgoaular/nanoxml/raw/master/LICENSE.txt",
                   attachedTo = "intellij.platform.util.nanoxml")
      .suppliedByPersons("Marc De Scheemaecker", "Saul Hidalgo"),
    LibraryLicense(name = "nest_asyncio", attachedTo = "intellij.python.community.impl",
                   url = "https://github.com/erdewit/nest_asyncio", license = "BSD 2-Clause License",
                   licenseUrl = "https://github.com/erdewit/nest_asyncio/blob/master/LICENSE",
                   version = LibraryLicense.CUSTOM_REVISION),
    LibraryLicense(name = "net.loomchild.segment", libraryName = "net.loomchild:segment:2.0.1",
                   url = "https://github.com/loomchild/segment")
      .mit("https://github.com/loomchild/segment/blob/master/LICENSE.txt")
      .suppliedByPersons("Jarek Lipski"),
    LibraryLicense(name = "netty-buffer", libraryName = "netty-buffer", url = "https://netty.io").apache()
      .suppliedByOrganizations("The Netty project"),
    LibraryLicense(name = "netty-codec-http", libraryName = "netty-codec-http", url = "https://netty.io").apache()
      .suppliedByOrganizations("The Netty project"),
    LibraryLicense(name = "netty-handler-proxy", libraryName = "netty-handler-proxy", url = "https://netty.io").apache()
      .suppliedByOrganizations("The Netty project"),
    LibraryLicense(libraryName = "ngram-slp", url = "https://github.com/SLP-team/SLP-Core")
      .mit("https://github.com/SLP-team/SLP-Core/blob/master/LICENSE")
      .suppliedByOrganizations("SLP-team"),
    LibraryLicense(name = "Objenesis", libraryName = "Objenesis", url = "https://objenesis.org/").apache()
      .suppliedByPersons("Henri Tremblay", "Joe Walnes", "Leonardo Mesquita"),
    LibraryLicense(name = "OkHttp", libraryName = "okhttp", url = "https://square.github.io/okhttp/")
      .apache("https://square.github.io/okhttp/#license"),
    LibraryLicense(libraryName = "opentelemetry", url = "https://opentelemetry.io/",
                   licenseUrl = "https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE", license = "Apache 2.0")
      .suppliedByOrganizations("The OpenTelemetry Authors"),
    LibraryLicense(libraryName = "opentelemetry-exporter-otlp", url = "https://opentelemetry.io/",
                   licenseUrl = "https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE", license = "Apache 2.0")
      .suppliedByOrganizations("The OpenTelemetry Authors"),
    LibraryLicense(libraryName = "opentelemetry-exporter-otlp-common", url = "https://opentelemetry.io/",
                   licenseUrl = "https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE", license = "Apache 2.0")
      .suppliedByOrganizations("The OpenTelemetry Authors"),
    LibraryLicense(libraryName = "opentelemetry-extension-kotlin", url = "https://opentelemetry.io/",
                   licenseUrl = "https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE", license = "Apache 2.0")
      .suppliedByOrganizations("The OpenTelemetry Authors"),
    LibraryLicense(libraryName = "opentelemetry-semconv", url = "https://opentelemetry.io/",
                   licenseUrl = "https://github.com/open-telemetry/semantic-conventions-java/blob/main/LICENSE", license = "Apache 2.0")
      .suppliedByOrganizations("The OpenTelemetry Authors"),
    LibraryLicense(libraryName = "opentest4j", url = "https://github.com/ota4j-team/opentest4j")
      .apache("https://github.com/ota4j-team/opentest4j/blob/master/LICENSE"),
    LibraryLicense(name = "OverlayScrollbars", attachedTo = "intellij.idea.community.main",
                   url = "https://kingsora.github.io/OverlayScrollbars", version = "2.1.1")
      .mit("https://github.com/KingSora/OverlayScrollbars/blob/master/LICENSE"),
    LibraryLicense(name = "Package Search API Models", libraryName = "package-search-api-models",
                   url = "https://github.com/JetBrains/package-search-api-models").apache(),
    LibraryLicense(name = "Package Search Version Utils", libraryName = "package-search-version-utils",
                   url = "https://github.com/JetBrains/package-search-version-utils").apache(),
    LibraryLicense(name = "PEPK", libraryName = "pepk", url = "https://source.android.com/",
                   version = LibraryLicense.CUSTOM_REVISION).apache(),
    androidDependency(name = "Perfetto Library", libraryName = "trace-perfetto-library"),
    androidDependency(name = "Perfetto protos", libraryName = "perfetto-proto"),

    LibraryLicense(name = "pip", attachedTo = "intellij.python", version = "20.3.4",
                   url = "https://pip.pypa.io/")
      .mit("https://github.com/pypa/pip/blob/main/LICENSE.txt"),
    LibraryLicense(name = "plexus-archiver", libraryName = "plexus-archiver",
                   url = "https://github.com/codehaus-plexus/plexus-archiver")
      .apache("https://github.com/codehaus-plexus/plexus-archiver/blob/master/LICENSE")
      .suppliedByOrganizations("The Codehaus Foundation, Inc."),

    LibraryLicense(name = "Plexus Utils", libraryName = "plexus-utils",
                   url = "https://github.com/codehaus-plexus/plexus-utils")
      .apache("https://github.com/codehaus-plexus/plexus-utils/blob/master/LICENSE.txt")
      .suppliedByOrganizations("The Codehaus Foundation, Inc."),
    LibraryLicense(name = "PLY", attachedTo = "intellij.python", version = "3.7", url = "https://www.dabeaz.com/ply/").newBsd(),

    LibraryLicense(libraryName = "pngencoder", url = "https://github.com/pngencoder/pngencoder")
      .mit("https://github.com/pngencoder/pngencoder/blob/develop/LICENSE"),

    LibraryLicense(name = "pockets", attachedTo = "intellij.python", version = "0.9.1",
                   url = "https://pockets.readthedocs.io/")
      .newBsd("https://github.com/RobRuana/pockets/blob/master/LICENSE"),
    LibraryLicense(name = "Protocol Buffers", libraryName = "protobuf", url = "https://developers.google.com/protocol-buffers")
      .newBsd("https://github.com/google/protobuf/blob/master/LICENSE"),
    LibraryLicense(name = "Proxy Vole", libraryName = "proxy-vole", url = "https://github.com/akuhtz/proxy-vole")
      .apache("https://github.com/akuhtz/proxy-vole/blob/master/LICENSE.md"),
    LibraryLicense(name = "pty4j", libraryName = "pty4j",
                   url = "https://github.com/JetBrains/pty4j")
      .eplV1("https://github.com/JetBrains/pty4j/blob/master/LICENSE"),
    LibraryLicense(name = "PureJavaComm", libraryName = "pty4j", transitiveDependency = true, version = "0.0.11.1",
                   url = "https://github.com/nyholku/purejavacomm")
      .newBsd("https://github.com/nyholku/purejavacomm/blob/master/LICENSE.txt"),
    LibraryLicense(name = "pycodestyle", attachedTo = "intellij.python", version = "2.8.0",
                   url = "https://pycodestyle.pycqa.org/")
      .mit("https://github.com/PyCQA/pycodestyle/blob/main/LICENSE"),
    LibraryLicense(name = "pyparsing", attachedTo = "intellij.python", version = "1.5.6",
                   url = "https://github.com/pyparsing/pyparsing/")
      .mit("https://github.com/pyparsing/pyparsing/blob/master/LICENSE"),

    LibraryLicense(name = "qdox-java-parser", libraryName = "qdox-java-parser",
                   url = "https://github.com/paul-hammant/qdox")
      .apache("https://github.com/paul-hammant/qdox/blob/master/LICENSE.txt"),

    LibraryLicense(name = "R8 DEX shrinker", libraryName = "jb-r8", url = "https://r8.googlesource.com/r8")
      .newBsd("https://r8.googlesource.com/r8/+/refs/heads/main/LICENSE")
      .suppliedByOrganizations(Suppliers.GOOGLE),

    LibraryLicense(name = "rd core", libraryName = "rd-core",
                   url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-core")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),
    LibraryLicense(name = "rd framework", libraryName = "rd-framework",
                   url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-framework")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "rd generator", libraryName = "rd-gen",
                   url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-gen")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),
    LibraryLicense(name = "rd Swing integration", libraryName = "rd-swing",
                   url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-swing")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),
    LibraryLicense(name = "rd text buffers", libraryName = "rd-text",
                   url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-text")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),
    LibraryLicense(name = "Reactive Streams", libraryName = "reactivestreams.reactive.streams",
                   url = "https://github.com/reactive-streams/reactive-streams-jvm")
      .mit("https://github.com/reactive-streams/reactive-streams-jvm/blob/master/LICENSE"),
    LibraryLicense(name = "Relax NG Object Model", libraryName = "rngom-20051226-patched.jar",
                   url = "https://github.com/kohsuke/rngom", version = LibraryLicense.CUSTOM_REVISION)
      .mit("https://github.com/kohsuke/rngom/blob/master/licenceheader.txt"),
    LibraryLicense(name = "Rhino JavaScript Engine", libraryName = "rhino", license = "MPL 1.1",
                   url = "https://github.com/mozilla/rhino", licenseUrl = "https://www.mozilla.org/MPL/MPL-1.1.html"),
    LibraryLicense(name = "Roboto", attachedTo = "intellij.platform.resources", version = "1.100141",
                   url = "https://github.com/googlefonts/roboto")
      .apache("https://github.com/google/roboto/blob/master/LICENSE"),
    LibraryLicense(name = "roman", attachedTo = "intellij.python", version = "1.4.0",
                   url = "https://docutils.sourceforge.io/docutils/utils/roman.py",
                   license = "Python 2.1.1 license",
                   licenseUrl = "https://www.python.org/download/releases/2.1.1/license/"),
    LibraryLicense(libraryName = "sa-jdwp", license = "GPL 2.0 + Classpath", url = "https://github.com/JetBrains/jdk-sa-jdwp",
                   licenseUrl = "https://github.com/JetBrains/jdk-sa-jdwp/raw/master/LICENSE.txt"),
    LibraryLicense(libraryName = "Saxon-6.5.5", version = "6.5.5", license = "Mozilla Public License",
                   url = "https://saxon.sourceforge.net/",
                   licenseUrl = "https://www.mozilla.org/MPL/"),
    LibraryLicense(libraryName = "Saxon-9HE", version = "9", license = "Mozilla Public License", url = "https://saxon.sourceforge.net/",
                   licenseUrl = "https://www.mozilla.org/MPL/"),
    LibraryLicense(name = "setuptools", attachedTo = "intellij.python", version = "44.1.1",
                   url = "https://setuptools.pypa.io/")
      .mit("https://github.com/pypa/setuptools/blob/main/LICENSE"),
    LibraryLicense(name = "six.py", attachedTo = "intellij.python", version = "1.9.0",
                   url = "https://six.readthedocs.io/",
                   licenseUrl = "https://github.com/benjaminp/six/blob/master/LICENSE")
      .mit("https://github.com/benjaminp/six/blob/master/LICENSE"),
    LibraryLicense(libraryName = "slf4j-api", url = "https://slf4j.org/")
      .mit("https://www.slf4j.org/license.html")
      .suppliedByOrganizations("QOS.ch Sarl"),
    LibraryLicense(libraryName = "slf4j-jdk14", url = "https://slf4j.org/")
      .mit("https://www.slf4j.org/license.html")
      .suppliedByOrganizations("QOS.ch Sarl"),
    LibraryLicense(name = "SnakeYAML", libraryName = "snakeyaml",
                   url = "https://bitbucket.org/snakeyaml/snakeyaml/")
      .apache("https://bitbucket.org/snakeyaml/snakeyaml/src/master/LICENSE.txt")
      .suppliedByPersons("Andrey Somov", "Alexander Maslov", "Jordan Angold"),
    LibraryLicense(name = "snakeyaml-engine", libraryName = "snakeyaml-engine",
                   url = "https://bitbucket.org/snakeyaml/snakeyaml-engine/")
      .apache("https://bitbucket.org/snakeyaml/snakeyaml-engine/src/master/LICENSE.txt")
      .suppliedByPersons("Andrey Somov", "Alexander Maslov"),
    LibraryLicense(name = "Sonatype Nexus: Indexer", attachedTo = "intellij.maven.server.m3.common", version = "3.0.4",
                   additionalLibraryNames = listOf("org.sonatype.nexus:nexus-indexer:3.0.4",
                                                   "org.sonatype.nexus:nexus-indexer-artifact:1.0.1"),
                   url = "https://nexus.sonatype.org/").eplV1(),
    LibraryLicense(name = "SourceCodePro", attachedTo = "intellij.platform.resources", version = "2.010", license = "OFL",
                   url = "https://github.com/adobe-fonts/source-code-pro",
                   licenseUrl = "https://github.com/adobe-fonts/source-code-pro/blob/master/LICENSE.md"),
    LibraryLicense(name = "sphinxcontrib-napoleon", attachedTo = "intellij.python", version = "0.7",
                   url = "https://sphinxcontrib-napoleon.readthedocs.io/",
                   licenseUrl = "https://github.com/sphinx-contrib/napoleon/blob/master/LICENSE").simplifiedBsd(),
    androidDependency(name = "SQLite Inspector Proto", libraryName = "sqlite-inspector-proto"),
    LibraryLicense(name = "Squareup Okio", libraryName = "squareup.okio.jvm", url = "https://github.com/square/okio")
      .apache("https://github.com/square/okio/blob/master/LICENSE.txt")
      .suppliedByOrganizations("Square, Inc."),
    LibraryLicense(name = "Squareup Wire", libraryName = "squareup.wire.runtime.jvm", url = "https://github.com/square/wire")
      .apache("https://github.com/square/wire/blob/master/LICENSE.txt")
      .suppliedByOrganizations("Square, Inc."),
    LibraryLicense(name = "ssh-nio-fs", libraryName = "ssh-nio-fs",
                   url = "https://github.com/JetBrains/intellij-deps-ssh-nio-fs")
      .mit("https://github.com/JetBrains/intellij-deps-ssh-nio-fs/blob/master/LICENSE")
      .forkedFrom(sourceCodeUrl = "https://github.com/lucastheisen/jsch-nio",
                  mavenRepositoryUrl = "https://repo1.maven.org/maven2",
                  groupId = "com.pastdev", artifactId = "jsch-nio",
                  version = "1.0.14",
                  authors = "Lucas Theisen"),
    LibraryLicense(name = "StreamEx", libraryName = "StreamEx",
                   url = "https://github.com/amaembo/streamex")
      .apache("https://github.com/amaembo/streamex/blob/master/LICENSE"),
    LibraryLicense(name = "Studio Protobuf", libraryName = "studio-proto", license = "protobuf",
                   url = "https://github.com/protocolbuffers/protobuf",
                   licenseUrl = "https://github.com/protocolbuffers/protobuf/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.GOOGLE),
    LibraryLicense(name = "swingx", libraryName = "swingx", license = "LGPL 2.1",
                   url = "https://central.sonatype.com/artifact/org.swinglabs/swingx-core/1.6.2-2",
                   licenseUrl = "https://www.opensource.org/licenses/lgpl-2.1.php")
      .suppliedByOrganizations("Sun Microsystems, Inc."),
    // for tensorflow-lite-metadata module library in android.sdktools.mlkit-common
    LibraryLicense(name = "TensorFlow Lite Metadata Library", libraryName = "tensorflow-lite-metadata",
                   url = "https://tensorflow.org/lite").apache(),
    LibraryLicense(libraryName = "TestNG", url = "https://testng.org/doc/")
      .apache("https://github.com/cbeust/testng/blob/master/LICENSE.txt"),
    LibraryLicense(name = "Thrift", libraryName = "libthrift", url = "https://thrift.apache.org/")
      .apache("https://github.com/apache/thrift/blob/master/LICENSE"),
    LibraryLicense(name = "thriftpy2", attachedTo = "intellij.python", version = "0.4.13",
                   url = "https://github.com/Thriftpy/thriftpy2/")
      .mit("https://github.com/Thriftpy/thriftpy2/blob/master/LICENSE"),
    // for traceprocessor-proto module library in intellij.android.profilersAndroid
    androidDependency(name = "TraceProcessor Daemon Protos", libraryName = "traceprocessor-protos"),
    LibraryLicense(name = "Trang", libraryName = "trang-core.jar",
                   url = "https://relaxng.org/jclark/trang.html",
                   version = LibraryLicense.CUSTOM_REVISION)
      .newBsd("https://opensource.org/license/bsd-3-clause/"),
    androidDependency(name = "Transport Pipeline", libraryName = "transport-proto"),

    LibraryLicense(name = "Trove4j (JetBrains's fork)", libraryName = "trove", license = "LGPL",
                   url = "https://github.com/JetBrains/intellij-deps-trove4j",
                   licenseUrl = "https://github.com/JetBrains/intellij-deps-trove4j/blob/master/LICENSE.txt")
      .forkedFrom(sourceCodeUrl = "https://sourceforge.net/p/trove4j/cvs", groupId = "net.sf.trove4j", artifactId = "trove4j"),
    LibraryLicense(name = "Typeshed", attachedTo = "intellij.python", version = LibraryLicense.CUSTOM_REVISION,
                   url = "https://github.com/python/typeshed")
      .apache("https://github.com/python/typeshed/blob/master/LICENSE"),
    LibraryLicense(name = "unit-api", libraryName = "javax.measure:unit-api:1.0",
                   url = "https://github.com/unitsofmeasurement/unit-api")
      .newBsd("https://github.com/unitsofmeasurement/unit-api/blob/master/LICENSE")
      .suppliedByPersons(
        "Jean-Marie Dautelle", "Werner Keil", "Otávio Gonçalves de Santana",
        "Martin Desruisseaux", "Thodoris Bais", "Daniel Dias", "Jacob Glickman",
        "Magesh Kasthuri", "Chris Senior", "Leonardo de Moura Rocha Lima", "Almas Shaikh",
        "Karen Legrand", "Rajmahendra Hegde", "Mohamed Mahmoud Taman", "Werner Keil",
        "Mohammed Al-Moayed", "Werner Keil"
      ),
    LibraryLicense(name = "uom-lib-common", libraryName = "tech.uom.lib:uom-lib-common:1.1",
                   url = "https://github.com/unitsofmeasurement/uom-lib")
      .newBsd("https://github.com/unitsofmeasurement/uom-lib/blob/master/LICENSE")
      .suppliedByPersons("Jean-Marie Dautelle", "Werner Keil"),
    LibraryLicense(libraryName = "Velocity", url = "https://velocity.apache.org/")
      .apache("https://gitbox.apache.org/repos/asf?p=velocity-engine.git;a=blob_plain;f=LICENSE;hb=HEAD")
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "virtualenv", attachedTo = "intellij.python", version = "20.13.0",
                   url = "https://virtualenv.pypa.io/")
      .mit("https://github.com/pypa/virtualenv/blob/main/LICENSE"),
    LibraryLicense(name = "Visual Studio Code", attachedTo = "intellij.textmate", version = "1.33.1",
                   url = "https://github.com/Microsoft/vscode/",
                   licenseUrl = "https://github.com/Microsoft/vscode-react-native/blob/master/LICENSE.txt").mit(),
    LibraryLicense(name = "weberknecht", libraryName = "weberknecht-0.1.5.jar", version = "0.1.5",
                   url = "https://github.com/pelotoncycle/weberknecht")
      .apache("https://github.com/pelotoncycle/weberknecht/blob/master/src/de/roderick/weberknecht/WebSocket.java"),
    LibraryLicense(libraryName = "winp", url = "https://github.com/jenkinsci/winp")
      .mit("https://github.com/jenkinsci/winp/blob/master/LICENSE.txt")
      .suppliedByPersons("Kohsuke Kawaguchi"),
    // for workmanager-inspector-proto module library in intellij.android.app-inspection.inspectors.workmanager.model
    androidDependency(name = "WorkManager Inspector Proto", libraryName = "workmanager-inspector-proto"),
    LibraryLicense(name = "Xalan", libraryName = "Xalan-2.7.2", url = "https://xalan.apache.org/xalan-j/")
      .apache("https://xalan.apache.org/xalan-j/#license")
      .suppliedByOrganizations(Suppliers.APACHE),
    LibraryLicense(libraryName = "Xerces", url = "https://xerces.apache.org/xerces2-j/")
      .apache("https://svn.apache.org/repos/asf/xerces/java/trunk/LICENSE")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense(name = "Xerial SQLite JDBC", libraryName = "sqlite", url = "https://github.com/xerial/sqlite-jdbc")
      .apache("https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE")
      .suppliedByOrganizations("Xerial Project"),
    LibraryLicense(name = "Xerial SQLite JDBC", libraryName = "sqlite-native", url = "https://github.com/xerial/sqlite-jdbc")
      .apache("https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE")
      .suppliedByOrganizations("Xerial Project"),

    LibraryLicense(name = "xml-apis-ext", libraryName = "xml-apis-ext",
                   url = "https://xerces.apache.org/xml-commons/components/external").apache()
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "xml-resolver", libraryName = "xml-resolver",
                   url = "https://xml.apache.org/commons/components/resolver/").apache()
      .suppliedByOrganizations("The Apache Software Foundation"),
    LibraryLicense(name = "XMLBeans", libraryName = "XmlBeans",
                   url = "https://xmlbeans.apache.org/",
                   licenseUrl = "https://svn.jetbrains.org/idea/Trunk/bundled/WebServices/resources/lib/xmlbeans-2.3.0/xmlbeans.LICENSE").apache()
      .suppliedByPersons("Cezar Andrei", "Radu Preotiuc", "Radu Preotiuc", "Wing Yew Poon", "Jacob Danner", "POI Team"),
    LibraryLicense(name = "XmlRPC", libraryName = "XmlRPC",
                   url = "https://ws.apache.org/xmlrpc/xmlrpc2/")
      .apache("https://ws.apache.org/xmlrpc/xmlrpc2/license.html")
      .suppliedByPersons(
        "Daniel Rall", "Jon Scott Stevens", "John Wilson",
        "Jochen Wiedmann", "Jason van Zyl", "Siegfried Goeschl",
        "Andrew Evers", "Henri Gomez", "Ryan Hoegg",
        "Leonard Richarson", "Hannes Wallnoefer"
      ),
    LibraryLicense(name = "XSLT Debugger RMI Stubs",
                   libraryName = "RMI Stubs",
                   url = "https://confluence.jetbrains.com/display/CONTEST/XSLT-Debugger",
                   version = LibraryLicense.CUSTOM_REVISION).apache(),
    LibraryLicense(name = "XStream", libraryName = "XStream",
                   url = "https://x-stream.github.io/")
      .newBsd("https://x-stream.github.io/license.html")
      .suppliedByOrganizations("XStream Committers"),
    LibraryLicense(name = "XZ for Java", libraryName = "xz", license = "Public Domain",
                   url = "https://tukaani.org/xz/java.html",
                   licenseUrl = "https://git.tukaani.org/?p=xz-java.git;a=blob;f=COPYING;h=8dd17645c4610c3d5eed9bcdd2699ecfac00406b;hb=refs/heads/master"),
    LibraryLicense(name = "zip-signer", libraryName = "zip-signer",
                   url = "https://github.com/JetBrains/marketplace-zip-signer")
      .apache("https://github.com/JetBrains/marketplace-zip-signer/blob/master/LICENSE"),
    LibraryLicense(name = "zstd-jni", libraryName = "zstd-jni",
                   url = "https://github.com/luben/zstd-jni")
      .simplifiedBsd("https://github.com/luben/zstd-jni/blob/master/LICENSE"),
    LibraryLicense(name = "zstd-jni-windows-aarch64", libraryName = "zstd-jni-windows-aarch64",
                   url = "https://github.com/VladRassokhin/zstd-jni")
      .simplifiedBsd("https://github.com/luben/zstd-jni/blob/master/LICENSE")
      .forkedFrom(sourceCodeUrl = "https://github.com/luben/zstd-jni",
                  mavenRepositoryUrl = "https://repo1.maven.org/maven2",
                  groupId = "com.github.luben", artifactId = "zstd-jni",
                  version = "1.5.2-5"),
    jetbrainsLibrary("change-reminder-prediction-model"),
    jetbrainsLibrary("cloud-config-client"),
    jetbrainsLibrary("completion-log-events"),
    jetbrainsLibrary("completion-performance-kotlin"),
    jetbrainsLibrary("completion-ranking-cpp-exp"),
    jetbrainsLibrary("completion-ranking-css-exp"),
    jetbrainsLibrary("completion-ranking-dart-exp"),
    jetbrainsLibrary("completion-ranking-go-exp"),
    jetbrainsLibrary("completion-ranking-html-exp"),
    jetbrainsLibrary("completion-ranking-java"),
    jetbrainsLibrary("completion-ranking-java-exp"),
    jetbrainsLibrary("completion-ranking-java-exp2"),
    jetbrainsLibrary("completion-ranking-js-exp"),
    jetbrainsLibrary("completion-ranking-kotlin"),
    jetbrainsLibrary("completion-ranking-kotlin-exp"),
    jetbrainsLibrary("completion-ranking-php-exp"),
    jetbrainsLibrary("completion-ranking-python-exp"),
    jetbrainsLibrary("completion-ranking-python-with-full-line"),
    jetbrainsLibrary("completion-ranking-ruby-exp"),
    jetbrainsLibrary("completion-ranking-rust-exp"),
    jetbrainsLibrary("completion-ranking-scala-exp"),
    jetbrainsLibrary("completion-ranking-swift-exp"),
    jetbrainsLibrary("completion-ranking-typescript-exp"),
    jetbrainsLibrary("debugger-agent"),
    jetbrainsLibrary("debugger-memory-agent"),
    jetbrainsLibrary("download-pgp-verifier"),
    jetbrainsLibrary("file-prediction-model"),
    jetbrainsLibrary("find-action-model"),
    jetbrainsLibrary("find-action-model-experimental"),
    jetbrainsLibrary("find-all-model-experimental"),
    jetbrainsLibrary("find-classes-model"),
    jetbrainsLibrary("find-classes-model-experimental"),
    jetbrainsLibrary("find-file-model"),
    jetbrainsLibrary("find-file-model-experimental"),
    jetbrainsLibrary("git-learning-project"),
    jetbrainsLibrary("intellij.remoterobot.remote.fixtures"),
    jetbrainsLibrary("intellij.remoterobot.robot.server.core"),
    jetbrainsLibrary("jetbrains.intellij.deps.rwmutex.idea"),
    jetbrainsLibrary("jshell-frontend"),
    jetbrainsLibrary("jvm-native-trusted-roots"),
    jetbrainsLibrary("kotlin-gradle-plugin-idea"),
    jetbrainsLibrary("kotlin-gradle-plugin-idea-proto"),
    jetbrainsLibrary("kotlin-script-runtime"),
    jetbrainsLibrary("kotlin-test"),
    jetbrainsLibrary("kotlin-tooling-core"),
    jetbrainsLibrary("kotlinc.allopen-compiler-plugin"),
    jetbrainsLibrary("kotlinc.analysis-api-providers"),
    jetbrainsLibrary("kotlinc.analysis-project-structure"),
    jetbrainsLibrary("kotlinc.android-extensions-compiler-plugin"),
    jetbrainsLibrary("kotlinc.assignment-compiler-plugin"),
    jetbrainsLibrary("kotlinc.high-level-api"),
    jetbrainsLibrary("kotlinc.high-level-api-fe10"),
    jetbrainsLibrary("kotlinc.high-level-api-fir"),
    jetbrainsLibrary("kotlinc.high-level-api-fir-tests"),
    jetbrainsLibrary("kotlinc.high-level-api-impl-base"),
    jetbrainsLibrary("kotlinc.high-level-api-impl-base-tests"),
    jetbrainsLibrary("kotlinc.incremental-compilation-impl-tests"),
    jetbrainsLibrary("kotlinc.kotlin-build-common-tests"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-cli"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-common"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-fe10"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-fir"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-ir"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-tests"),
    jetbrainsLibrary("kotlinc.kotlin-dist"),
    jetbrainsLibrary("kotlinc.kotlin-gradle-statistics"),
    jetbrainsLibrary("kotlinc.kotlin-jps-common"),
    jetbrainsLibrary("kotlinc.kotlin-jps-plugin-classpath"),
    jetbrainsLibrary("kotlinc.kotlin-objcexport-header-generator"),
    jetbrainsLibrary("kotlinc.kotlin-script-runtime"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-common"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-compiler-impl"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-jvm"),
    jetbrainsLibrary("kotlinc.kotlinx-serialization-compiler-plugin"),
    jetbrainsLibrary("kotlinc.lombok-compiler-plugin"),
    jetbrainsLibrary("kotlinc.low-level-api-fir"),
    jetbrainsLibrary("kotlinc.noarg-compiler-plugin"),
    jetbrainsLibrary("kotlinc.parcelize-compiler-plugin"),
    jetbrainsLibrary("kotlinc.sam-with-receiver-compiler-plugin"),
    jetbrainsLibrary("kotlinc.scripting-compiler-plugin"),
    jetbrainsLibrary("kotlinc.symbol-light-classes"),
    jetbrainsLibrary("kotlinx-collections-immutable"),
    jetbrainsLibrary("kotlinx-coroutines-debug"),
    jetbrainsLibrary("ml-completion-prev-exprs-models"),
    jetbrainsLibrary("tcServiceMessages"),
    jetbrainsLibrary("terminal-completion-db-with-extensions"),
    jetbrainsLibrary("terminal-completion-spec"),
    jetbrainsLibrary("tips-idea-ce"),
    jetbrainsLibrary("tips-pycharm-community"),
    jetbrainsLibrary("workspace-model-codegen"),
  )

  private fun ffmpegLibraryLicense(libraryName: String): LibraryLicense {
    return LibraryLicense(
      name = libraryName,
      libraryName = libraryName,
      url = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/tags/studio-2022.3.1-beta2/common/m2/repository/org/bytedeco",
      license = "LGPL v2.1+",
      licenseUrl = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/tags/studio-2022.3.1-beta2/common/m2/repository/org/bytedeco/ffmpeg-LICENSE.md"
    ).suppliedByOrganizations(Suppliers.GOOGLE)
  }

  private fun androidDependency(name: String, libraryName: String = name, version: String? = null) =
    LibraryLicense(name = name, libraryName = libraryName, version = version,
                   url = "https://source.android.com/").apache("https://source.android.com/setup/start/licenses")
      .suppliedByOrganizations(Suppliers.GOOGLE)
}
