// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.LibraryLicense.Companion.jetbrainsLibrary
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers

/**
 * Defines information about licenses of libraries located in 'community', 'contrib' and 'android' repositories.
 */
object CommunityLibraryLicenses {
  @JvmStatic
  @Suppress("SpellCheckingInspection", "NonAsciiCharacters")
  val LICENSES_LIST: List<LibraryLicense> = listOf(
    LibraryLicense("A fast Java JSON schema validator", libraryName = "json-schema-validator", url = "https://github.com/networknt/json-schema-validator")
      .apache("https://github.com/networknt/json-schema-validator/blob/master/LICENSE"),

    LibraryLicense("aalto-xml", libraryName = "aalto-xml", url = "https://github.com/FasterXML/aalto-xml/")
      .apache("https://github.com/FasterXML/aalto-xml/blob/master/LICENSE"),

    androidDependency("AAPT Protos", libraryName = "aapt-proto"),

    LibraryLicense("AhoCorasickDoubleArrayTrie", libraryName = "com.hankcs:aho-corasick-double-array-trie", url = "https://github.com/hankcs/AhoCorasickDoubleArrayTrie")
      .apache("https://github.com/hankcs/AhoCorasickDoubleArrayTrie/blob/master/README.md#license")
      .suppliedByPersons("hankcs"),

    LibraryLicense("Allure java commons", libraryName = "io.qameta.allure.java.commons", url = "https://github.com/allure-framework/allure-java")
      .apache("https://github.com/allure-framework/allure-java/blob/master/README.md"),

    LibraryLicense("Amazon Ion Java", libraryName = "ion", url = "https://github.com/amazon-ion/ion-java")
      .apache("https://github.com/amazon-ion/ion-java/blob/master/LICENSE")
      .suppliedByOrganizations("Amazon Ion Team")
      .copyrightText("Copyright 2007-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved."),

    androidDependency("Android Baksmali", libraryName = "google-baksmali"),

    androidDependency("Android DEX library", libraryName = "google-dexlib2"),

    androidDependency("Android Gradle model", version = "0.4-SNAPSHOT", libraryName = null)
      .copy(attachedTo = "intellij.android.core", url = "https://android.googlesource.com/platform/tools/build/+/master/gradle-model/"),

    androidDependency("Android Instant Apps SDK API", version = LibraryLicense.CUSTOM_REVISION, libraryName = null)
      .copy(url = "https://source.android.com/", libraryName = "instantapps-api"),

    LibraryLicense("Android Jimfs library", libraryName = "jimfs", url = "https://github.com/google/jimfs")
      .apache("https://github.com/google/jimfs/blob/master/LICENSE"),

    androidDependency("Android Layout Library", libraryName = "layoutlib"),

    LibraryLicense("Android libwebp library", libraryName = "libwebp.jar", url = "https://github.com/webmproject/libwebp", version = LibraryLicense.CUSTOM_REVISION)
      .newBsd("https://github.com/webmproject/libwebp/blob/main/COPYING"),

    androidDependency("Android SDK Common", libraryName = "android.tools.sdk.common"),

    androidDependency("Android Studio Platform", libraryName = "studio-platform"),

    LibraryLicense("antlr4-runtime", libraryName = "antlr4-runtime", url = "https://github.com/antlr/antlr4")
      .newBsd("https://github.com/antlr/antlr4/blob/dev/LICENSE.txt"),

    LibraryLicense(libraryName = "apache.logging.log4j.to.slf4j", url = "https://ant.apache.org/")
      .apache("https://logging.apache.org/log4j/log4j-2.2/license.html")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Apache Ant", version = "1.9", libraryName = "Ant", url = "https://ant.apache.org/")
      .apache("https://ant.apache.org/license.html"),

    LibraryLicense("Apache Axis", libraryName = "axis-1.4", version = "1.4", url = "https://axis.apache.org/axis/")
      .apache("https://svn.apache.org/viewvc/axis/axis1/java/trunk/LICENSE?view=markup"),

    LibraryLicense("Apache Commons CLI", libraryName = "commons-cli", url = "https://commons.apache.org/proper/commons-cli/")
      .apache("https://github.com/apache/commons-cli/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Codec", libraryName = "commons-codec", url = "https://commons.apache.org/proper/commons-codec/")
      .apache("https://github.com/apache/commons-codec/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Collections", libraryName = "commons-collections", url = "https://commons.apache.org/proper/commons-collections/")
      .apache("https://github.com/apache/commons-collections/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Compress", libraryName = "commons-compress", url = "https://commons.apache.org/proper/commons-compress/")
      .apache("https://github.com/apache/commons-compress/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Apache Commons Discovery", libraryName = "commons-discovery", url = "https://commons.apache.org/dormant/commons-discovery/")
      .apache("https://commons.apache.org/dormant/commons-discovery/license.html")
      .copyrightText("Copyright © 2002-2011 The Apache Software Foundation. All Rights Reserved.")
      .suppliedByPersons(
        "Simone Tripodi", "James Strachan", "Robert Burrell Donkin", "Matthew Hawthorne",
        "Richard Sitze", "Craig R. McClanahan", "Costin Manolache", "Davanum Srinivas", "Rory Winston"
      ),

    LibraryLicense("Apache Commons HTTPClient", libraryName = "http-client-3.1", version = "3.1 (with patch by JetBrains)", url = "https://hc.apache.org/httpclient-3.x")
      .apache("https://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/LICENSE.txt?view=markup"),

    LibraryLicense("Apache Commons Imaging (JetBrains's fork)", libraryName = "commons-imaging", url = "https://github.com/JetBrains/intellij-deps-commons-imaging")
      .apache("https://github.com/JetBrains/intellij-deps-commons-imaging/blob/master/LICENSE.txt")
      .forkedFrom(
        groupId = "org.apache.commons",
        artifactId = "commons-imaging",
        revision = "fa201df06edefd329610d210d67caba6802b1211",
        sourceCodeUrl = "https://github.com/apache/commons-imaging"
      ),

    LibraryLicense("Apache Commons IO", libraryName = "commons-io", url = "https://commons.apache.org/proper/commons-io/")
      .apache("https://github.com/apache/commons-io/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Lang", libraryName = "commons-lang3", url = "https://commons.apache.org/proper/commons-lang/")
      .apache("https://github.com/apache/commons-lang/blob/master/LICENSE.txt")
      .suppliedByPersons(
        "Daniel Rall", "Robert Burrell Donkin", "James Carman", "Benedikt Ritter", "Rob Tompkins", "Stephen Colebourne",
        "Henri Yandell", "Steven Caswell", "Gary D. Gregory", "Fredrik Westermarck", "Niall Pemberton", "Matt Benson", "Joerg Schaible",
        "Oliver Heger", "Paul Benedict", "Duncan Jones", "Loic Guibert"
      )
      .copyrightText("Copyright © 2001-2023 The Apache Software Foundation. All Rights Reserved."),

    LibraryLicense("Apache Commons Logging", libraryName = "commons-logging", url = "https://commons.apache.org/proper/commons-logging/")
      .apache("https://github.com/apache/commons-logging/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Math", libraryName = "commons-math3", url = "https://commons.apache.org/proper/commons-math/")
      .apache("https://github.com/apache/commons-math/blob/master/LICENSE"),

    LibraryLicense("Apache Commons Net", libraryName = "commons-net", url = "https://commons.apache.org/proper/commons-net/")
      .apache("https://github.com/apache/commons-net/blob/master/LICENSE.txt"),

    LibraryLicense("Apache Commons Text", libraryName = "commons-text", url = "https://github.com/apache/commons-text")
      .apache("https://github.com/apache/commons-text/blob/master/LICENSE.txt")
      .copyrightText("Copyright 2014-2024 The Apache Software Foundation"),

    LibraryLicense("Apache Ivy", libraryName = "org.apache.ivy", url = "https://github.com/apache/ant-ivy")
      .apache("https://github.com/apache/ant-ivy/blob/master/LICENSE")
      .copyrightText("Copyright 2007-2019,2022-2023 The Apache Software Foundation")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Apache Lucene", libraryName = "lucene-core", url = "https://lucene.apache.org/java")
      .additionalLibraryNames(
        "lucene-suggest", "lucene-memory", "lucene-sandbox", "lucene-codecs", "lucene-highlighter", "lucene-queryparser",
        "lucene-queries", "lucene-analysis-common", "org.apache.lucene:lucene-core:2.4.1"
      )
      .apache("https://github.com/apache/lucene/blob/main/LICENSE.txt")
      .copyrightText("Copyright © 2011-2024 The Apache Software Foundation")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Apache Tuweni-Toml", libraryName = "tuweni-toml", url = "https://github.com/apache/incubator-tuweni/tree/main/toml")
      .apache("https://github.com/apache/incubator-tuweni/blob/main/LICENSE")
      .copyrightText("Copyright 2019-2023 The Apache Software Foundation"),

    LibraryLicense("AsciiDoc support for Visual Studio Code", version = "3.2.4", attachedTo = "intellij.textmate", url = "https://github.com/asciidoctor/asciidoctor-vscode")
      .mit("https://github.com/asciidoctor/asciidoctor-vscode/blob/master/README.md"),

    LibraryLicense("ASM (JetBrains's fork)", libraryName = "ASM", url = "https://github.com/JetBrains/intellij-deps-asm")
      .newBsd("https://github.com/JetBrains/intellij-deps-asm/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS)
      .forkedFrom(
        sourceCodeUrl = "https://gitlab.ow2.org/asm/asm",
        mavenRepositoryUrl = "https://repo1.maven.org/maven2",
        groupId = "org.ow2.asm", artifactId = "asm",
        version = "9.5",
        authors = "Guillaume Sauthier, Eric Bruneton, Eugene Kuleshov, Remi Forax"
      ),

    LibraryLicense("ASM Tools", libraryName = "asm-tools", url = "https://asm.ow2.io")
      .newBsd("https://asm.ow2.io/license.html"),

    LibraryLicense("AssertJ fluent assertions", libraryName = "assertJ", url = "https://github.com/assertj/assertj-core")
      .apache("https://github.com/assertj/assertj-core/blob/main/LICENSE.txt")
      .suppliedByPersons(
        "Pascal Schumacher", "Joel Costigliola", "Stefano Cordio", "Erhard Pointl", "Christian Rösch",
        "Julien Roy", "Régis Pouiller", "Florent Biville", "Patrick Allain"
      ),

    LibraryLicense("AssertJ Swing", libraryName = "assertj-swing", url = "https://github.com/assertj/assertj-swing")
      .apache("https://github.com/assertj/assertj-swing/blob/main/licence-header.txt")
      .suppliedByPersons("Joel Costigliola", "Joel Costigliola", "Christian Rösch", "Alex Ruiz", "Yvonne Wang", "Ansgar Konermann"),

    LibraryLicense("Atlassian Commonmark", libraryName = "atlassian.commonmark", url = "https://github.com/commonmark/commonmark-java")
      .simplifiedBsd("https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt")
      .additionalLibraryNames(
        "commonmark.ext.autolink",
        "commonmark.ext.gfm.tables",
        "commonmark.ext.gfm.strikethrough",
        "org.commonmark.commonmark"
      )
      .copyrightText("Copyright (c) 2015, Atlassian Pty Ltd")
      .suppliedByOrganizations("Atlassian Pty Ltd"),

    LibraryLicense("Autolink Java", libraryName = "org.nibor.autolink.autolink", url = "https://github.com/robinst/autolink-java")
      .mit("https://github.com/robinst/autolink-java/blob/main/LICENSE"),

    LibraryLicense("Automaton", libraryName = "automaton", url = "https://www.brics.dk/automaton/")
      .simplifiedBsd("https://github.com/cs-au-dk/dk.brics.automaton/blob/master/COPYING")
      .copyrightText("Copyright (c) 2001-2022 Anders Moeller"),

    LibraryLicense("Bash-Preexec", version = "0.5.0", attachedTo = "intellij.terminal", url = "https://github.com/rcaloras/bash-preexec")
      .mit("https://github.com/rcaloras/bash-preexec/blob/master/LICENSE.md"),

    LibraryLicense("batik", libraryName = "batik-transcoder", url = "https://xmlgraphics.apache.org/batik/")
      .apache("https://xmlgraphics.apache.org/batik/license.html")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense(libraryName = "blockmap", url = "https://github.com/JetBrains/plugin-blockmap-patches")
      .apache("https://github.com/JetBrains/plugin-blockmap-patches/blob/master/LICENSE"),

    LibraryLicense("Bodymovin", version = "5.5.10", attachedTo = "intellij.platform.ide.newUiOnboarding", url = "https://github.com/airbnb/lottie-web")
      .mit("https://github.com/airbnb/lottie-web/blob/master/LICENSE.md"),

    LibraryLicense(libraryName = "bouncy-castle-pgp", url = "https://www.bouncycastle.org")
      .mit("https://www.bouncycastle.org/license.html")
      .suppliedByOrganizations("The Legion of the Bouncy Castle Inc."),

    LibraryLicense(libraryName = "bouncy-castle-provider", url = "https://www.bouncycastle.org")
      .mit("https://www.bouncycastle.org/license.html")
      .suppliedByOrganizations("The Legion of the Bouncy Castle Inc."),

    LibraryLicense("Brotli", libraryName = "brotli-dec", url = "https://github.com/google/brotli")
      .mit("https://github.com/google/brotli/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.GOOGLE),

    LibraryLicense("caffeine", libraryName = "caffeine", url = "https://github.com/ben-manes/caffeine")
      .apache("https://github.com/ben-manes/caffeine/blob/master/LICENSE")
      .suppliedByPersons("Ben Manes"),

    LibraryLicense("CGLib", libraryName = "cglib", url = "https://github.com/cglib/cglib/")
      .apache("https://github.com/cglib/cglib/blob/master/LICENSE")
      .copyrightText("Copyright (c) The Apache Software Foundation")
      .suppliedByPersons("cglib project contributors"),

    LibraryLicense("classgraph", libraryName = "classgraph", url = "https://github.com/classgraph/classgraph")
      .license("codehaus", "https://github.com/codehaus/classworlds/blob/master/classworlds/LICENSE.txt"),

    LibraryLicense("Clikt", libraryName = "clikt", url = "https://github.com/ajalt/clikt")
      .apache("https://github.com/ajalt/clikt/blob/master/LICENSE.txt")
      .copyrightText("Copyright 2018 AJ Alt")
      .suppliedByOrganizations("AJ Alt"),

    LibraryLicense("CMake For VisualStudio Code", version = "0.0.17", attachedTo = "intellij.textmate", url = "https://github.com/twxs/vs.language.cmake")
      .mit("https://github.com/twxs/vs.language.cmake/blob/master/LICENSE"),

    // For loading images in Compose (used in Markdown preview, for example)
    LibraryLicense("Coil", libraryName = "io.coil.kt.coil3.compose.jvm", url = "https://github.com/coil-kt/coil")
      .additionalLibraryNames(
        "io.coil.kt.coil3.network.ktor3.jvm",
        "io.coil.kt.coil3.svg.jvm",
      )
      .apache("https://github.com/coil-kt/coil/blob/main/README.md#license")
      .copyrightText("Copyright 2025 Coil Contributors"),

    LibraryLicense("Command Line Interface Parser for Java", libraryName = "cli-parser", url = "https://github.com/spullara/cli-parser?tab=readme-ov-file")
      .apache("https://github.com/spullara/cli-parser/blob/95edeb2d1a21fb13760b4f96f976a7f3108e0942/README.md?plain=1#L65")
      .copyrightText("Copyright 2012 Sam Pullara"),

    LibraryLicense("Common Annotations for the JavaTM Platform API", libraryName = "javax.annotation-api", url = "https://github.com/javaee/javax.annotation")
      .cddl11("https://github.com/javaee/javax.annotation/blob/master/LICENSE"),

    // for ui-animation-tooling-internal module library in intellij.android.compose-designer
    LibraryLicense("Compose Animation Tooling", libraryName = "ui-animation-tooling-internal", version = "0.1.0-SNAPSHOT", url = "https://source.android.com/")
      .apache("https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:LICENSE.txt")
      .suppliedByOrganizations(Suppliers.GOOGLE),

    LibraryLicense("Compose Multiplatform", libraryName = "compose-foundation-desktop", url = "https://github.com/JetBrains/compose-multiplatform")
      .apache("https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt")
      .additionalLibraryNames(
        "org.jetbrains.compose.components.components.resources.desktop",
        "org.jetbrains.compose.components.components.resources"
      )
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Compose Multiplatform Compiler", libraryName = "jetbrains.compose.compiler.hosted", url = "https://github.com/JetBrains/compose-multiplatform")
      .apache("https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Compose Multiplatform File Picker", libraryName = "com.darkrockstudios.mpfilepicker", url = "https://github.com/Wavesonics/compose-multiplatform-file-picker")
      .mit("https://github.com/Wavesonics/compose-multiplatform-file-picker/blob/master/LICENSE")
      .additionalLibraryNames(
        "com.darkrockstudios.mpfilepicker.jvm"
      ),

    // For ADB wireless QR Code generation
    LibraryLicense("Core barcode encoding/decoding library", url = "https://github.com/zxing/zxing/tree/master/core", libraryName = "zxing-core")
      .apache("https://github.com/zxing/zxing/blob/master/LICENSE")
      .suppliedByOrganizations("ZXing Authors"),

    LibraryLicense("coverage-report", libraryName = "coverage-report", url = "https://github.com/JetBrains/coverage-report")
      .apache("https://github.com/JetBrains/coverage-report/blob/master/LICENSE"),

    LibraryLicense("coverage.py", version = "4.2.0", attachedTo = "intellij.python", url = "https://coverage.readthedocs.io/")
      .apache("https://github.com/nedbat/coveragepy/blob/master/LICENSE.txt"),

    LibraryLicense("cucumber-core", libraryName = "cucumber-core-1", url = "https://github.com/cucumber/cucumber-jvm/blob/main/LICENSE")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENSE")
      .suppliedByOrganizations("SmartBear Software"),

    LibraryLicense("Cucumber-Expressions", libraryName = "cucumber-expressions", url = "https://github.com/cucumber/cucumber/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENSE")
      .suppliedByOrganizations("SmartBear Software"),

    LibraryLicense("Cucumber-Groovy", libraryName = "cucumber-groovy", url = "https://github.com/cucumber/cucumber-jvm/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENSE")
      .suppliedByOrganizations("SmartBear Software"),

    LibraryLicense("Cucumber-Java", libraryName = "cucumber-java", url = "https://github.com/cucumber/cucumber-jvm/")
      .mit("https://github.com/cucumber/cucumber-jvm/blob/main/LICENSE")
      .suppliedByOrganizations("SmartBear Software"),

    LibraryLicense("Dart Analysis Server", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.dart", url = "https://github.com/dart-lang/eclipse3")
      .eplV1("https://github.com/dart-archive/eclipse3/tree/master/docs"),

    LibraryLicense("Dart VM Service drivers", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.dart", url = "https://github.com/dart-lang/vm_service_drivers")
      .newBsd("https://github.com/dart-lang/vm_service_drivers/blob/master/LICENSE"),

    LibraryLicense("dbus-java", libraryName = "dbus-java", url = "https://github.com/hypfvieh/dbus-java")
      .lgpl2("https://github.com/hypfvieh/dbus-java/blob/dbus-java-3.0/LICENSE")
      .suppliedByPersons("David M. <hypfvieh@googlemail.com>"),

    LibraryLicense("Detekt", libraryName = "io.gitlab.arturbosch.detekt.api", url = "https://github.com/detekt/detekt")
      .apache("https://github.com/detekt/detekt/blob/master/LICENSE"),

    LibraryLicense("Detekt Compose Rules", libraryName = "io.nlopez.compose.rules.detekt", url = "https://github.com/mrmans0n/compose-rules")
      .apache("https://github.com/mrmans0n/compose-rules/blob/main/LICENSE.md"),

    LibraryLicense("docutils", version = "0.12", attachedTo = "intellij.python", url = "https://docutils.sourceforge.io/")
      .public("https://sourceforge.net/p/docutils/code/HEAD/tree/trunk/docutils/COPYING.rst"),

    LibraryLicense("dotenv-kotlin", libraryName = "io.github.cdimascio.dotenv.kotlin", url = "https://github.com/cdimascio/dotenv-kotlin")
      .apache("https://github.com/cdimascio/dotenv-kotlin/blob/master/LICENSE"),

    LibraryLicense("Eclipse JDT Core", version = "4.2.1", attachedTo = "intellij.platform.jps.build", url = "https://www.eclipse.org/jdt/core/index.php")
      .eplV2("https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/LICENSE"),

    LibraryLicense("Eclipse Layout Kernel", url = "https://www.eclipse.org/elk/", libraryName = "eclipse-layout-kernel")
      .eplV1("https://github.com/eclipse/elk/blob/master/LICENSE.md")
      .suppliedByOrganizations(Suppliers.ECLIPSE),

    LibraryLicense("EditorConfig Java Parser", libraryName = "ec4j-core", url = "https://github.com/ec4j/ec4j")
      .apache("https://github.com/ec4j/ec4j/blob/master/LICENSE")
      .suppliedByPersons("Peter Palaga", "Angelo Zerr"),

    LibraryLicense("emoji-java", libraryName = "com.vdurmont:emoji-java", url = "https://github.com/vdurmont/emoji-java")
      .mit("https://github.com/vdurmont/emoji-java/blob/master/LICENSE.md")
      .suppliedByPersons("Vincent DURMONT"),

    LibraryLicense("entities", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.vuejs", url = "https://github.com/fb55/entities")
      .simplifiedBsd("https://github.com/fb55/entities/blob/master/LICENSE"),

    LibraryLicense("epydoc", version = "3.0.1", attachedTo = "intellij.python", url = "https://epydoc.sourceforge.net/")
      .mit("https://epydoc.sourceforge.net/license.html"),

    LibraryLicense("fastutil", libraryName = "fastutil-min", url = "https://github.com/vigna/fastutil")
      .apache("https://github.com/vigna/fastutil/blob/master/LICENSE-2.0")
      .suppliedByPersons("Sebastiano Vigna"),

    ffmpegLibraryLicense("ffmpeg"),
    ffmpegLibraryLicense("ffmpeg-javacpp"),
    ffmpegLibraryLicense("ffmpeg-linux-x64"),
    ffmpegLibraryLicense("ffmpeg-macos-aarch64"),
    ffmpegLibraryLicense("ffmpeg-macos-x64"),
    ffmpegLibraryLicense("ffmpeg-windows-x64"),

    LibraryLicense("FiraCode", version = "1.206", attachedTo = "intellij.platform.resources", url = "https://github.com/tonsky/FiraCode")
      .license("OFL", "https://github.com/tonsky/FiraCode/blob/master/LICENSE"),

    LibraryLicense("FreeMarker", version = "2.3.30", attachedTo = "intellij.java.coverage", url = "https://freemarker.apache.org")
      .apache("https://freemarker.apache.org/docs/app_license.html"),

    LibraryLicense("gauge-java", libraryName = "com.thoughtworks.gauge:gauge-java", url = "https://github.com/getgauge/gauge-java/")
      .apache("https://github.com/getgauge/gauge-java/raw/master/LICENSE.txt"),

    LibraryLicense("Gherkin", libraryName = "gherkin", url = "https://github.com/cucumber/gherkin/tree/main")
      .mit("https://github.com/cucumber/gherkin/blob/main/LICENSE")
      .suppliedByOrganizations("Cucumber Ltd"),

    LibraryLicense("Gherkin keywords", version = "2.12.2", attachedTo = "intellij.gherkin", url = "https://github.com/cucumber/gherkin/tree/main")
      .mit("https://github.com/cucumber/gherkin/blob/main/LICENSE")
      .suppliedByOrganizations("Cucumber Ltd"),

    LibraryLicense(url = "https://github.com/oshi/oshi", libraryName = "github.oshi.core")
      .mit("https://github.com/oshi/oshi/blob/master/LICENSE")
      .suppliedByOrganizations("The OSHI Project Contributors"),

    LibraryLicense("googlecode.plist.dd", libraryName = "googlecode.plist.dd", url = "https://github.com/3breadt/dd-plist/")
      .mit("https://github.com/3breadt/dd-plist/blob/master/LICENSE.txt"),

    LibraryLicense(libraryName = "Gradle", url = "https://gradle.org/")
      .apache("https://github.com/gradle/gradle/blob/master/LICENSE")
      .suppliedByOrganizations("Gradle Inc."),

    LibraryLicense("GraphQL Java", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.graphql", url = "https://github.com/graphql-java/graphql-java")
      .mit("https://github.com/graphql-java/graphql-java/blob/master/LICENSE.md"),

    LibraryLicense("GraphQL Java Dataloader", libraryName = "graphql.java.dataloader", url = "https://github.com/graphql-java/java-dataloader")
      .apache("https://github.com/graphql-java/java-dataloader/blob/master/LICENSE"),

    LibraryLicense("Groovy", libraryName = "org.codehaus.groovy:groovy", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense("Groovy Ant", libraryName = "org.codehaus.groovy:groovy-ant", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense("Groovy JSON", libraryName = "org.codehaus.groovy:groovy-json", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense("Groovy JSR-223", libraryName = "org.codehaus.groovy:groovy-jsr223", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense("Groovy Templates", libraryName = "org.codehaus.groovy:groovy-templates", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense("Groovy XML", libraryName = "org.codehaus.groovy:groovy-xml", url = "https://groovy-lang.org/")
      .apache("https://github.com/apache/groovy/blob/master/LICENSE"),

    LibraryLicense(libraryName = "grpc-inprocess", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("gRPC Kotlin: Stub", libraryName = "grpc-kotlin-stub", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("gRPC: Core", libraryName = "grpc-core", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("gRPC: Netty Shaded", libraryName = "grpc-netty-shaded", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("gRPC: Protobuf", libraryName = "grpc-protobuf", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("gRPC: Stub", libraryName = "grpc-stub", url = "https://grpc.io/")
      .apache("https://github.com/grpc/grpc-java/blob/master/LICENSE")
      .suppliedByOrganizations("gRPC Authors"),

    LibraryLicense("Gson", libraryName = "gson", url = "https://github.com/google/gson")
      .apache("https://github.com/google/gson/blob/master/LICENSE"),

    LibraryLicense(libraryName = "Guava", url = "https://github.com/google/guava")
      .apache("https://github.com/google/guava/raw/master/LICENSE"),

    LibraryLicense("Hamcrest", libraryName = "hamcrest", url = "https://hamcrest.org/")
      .newBsd("https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE")
      .suppliedByPersons("Joe Walnes", "Nat Pryce", "Steve Freeman"),

    LibraryLicense("Hamcrest More Matchers", libraryName = "github.seregamorph.hamcrest.more.matchers", url = "https://github.com/seregamorph/hamcrest-more-matchers")
      .apache("https://github.com/seregamorph/hamcrest-more-matchers/blob/master/LICENSE")
      .suppliedByPersons("Sergey Chernov"),

    LibraryLicense(libraryName = "hash4j", url = "https://github.com/dynatrace-oss/hash4j")
      .apache("https://github.com/dynatrace-oss/hash4j/blob/main/LICENSE"),

    LibraryLicense("HashiCorp Syntax", version = "0.6.0", attachedTo = "intellij.textmate", url = "https://github.com/asciidoctor/asciidoctor-vscode")
      .mpl2("https://github.com/hashicorp/syntax/blob/main/LICENSE"),

    LibraryLicense("HDR Histogram", libraryName = "HdrHistogram", url = "https://github.com/HdrHistogram/HdrHistogram")
      .public("https://github.com/HdrHistogram/HdrHistogram/blob/master/LICENSE.txt")
      .suppliedByPersons("Gil Tene"),

    LibraryLicense("hppc", url = "https://github.com/carrotsearch/hppc", libraryName = "com.carrotsearch:hppc")
      .apache("https://github.com/carrotsearch/hppc/blob/master/LICENSE.txt")
      .suppliedByPersons("Stanisław Osiński", "Dawid Weiss", "Bruno Roustant"),

    LibraryLicense("htmlparser2", url = "https://github.com/fb55/htmlparser2", attachedTo = "intellij.vuejs", version = LibraryLicense.CUSTOM_REVISION)
      .mit("https://github.com/fb55/htmlparser2/blob/master/LICENSE"),

    LibraryLicense("HttpComponents HttpClient", libraryName = "http-client", url = "https://github.com/apache/httpcomponents-client/")
      .apache("https://github.com/apache/httpcomponents-client/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("HttpComponents HttpClient Fluent API", libraryName = "fluent-hc", url = "https://github.com/apache/httpcomponents-client/")
      .apache("https://github.com/apache/httpcomponents-client/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("ICU4J", libraryName = "icu4j", url = "https://icu.unicode.org/")
      .license("Unicode", "https://www.unicode.org/copyright.html"),

    LibraryLicense("imgscalr", libraryName = "imgscalr", url = "https://github.com/thebuzzmedia/imgscalr")
      .apache("https://github.com/rkalla/imgscalr/blob/master/LICENSE"),

    LibraryLicense("Inconsolata", version = "001.010", attachedTo = "intellij.platform.resources", url = "https://github.com/google/fonts/tree/main/ofl/inconsolata")
      .license("OFL", "https://github.com/google/fonts/blob/master/ofl/inconsolata/OFL.txt"),

    LibraryLicense("Incremental DOM", version = "0.7.0", attachedTo = "intellij.markdown", url = "https://github.com/google/incremental-dom")
      .apache("https://github.com/google/incremental-dom/blob/master/LICENSE"),

    LibraryLicense("indriya", libraryName = "tech.units:indriya:1.3", url = "https://github.com/unitsofmeasurement/indriya")
      .newBsd("https://github.com/unitsofmeasurement/indriya/blob/master/LICENSE")
      .suppliedByPersons(
        "Jean-Marie Dautelle", "Werner Keil", "Otávio Gonçalves de Santana",
        "Martin Desruisseaux", "Thodoris Bais", "Daniel Dias",
        "Jacob Glickman", "Magesh Kasthuri"
      ),

    LibraryLicense("ini4j (JetBrains's fork)", libraryName = "ini4j", url = "https://github.com/JetBrains/intellij-deps-ini4j")
      .apache("https://github.com/JetBrains/intellij-deps-ini4j/blob/master/LICENSE.txt")
      .forkedFrom(
        sourceCodeUrl = "https://sourceforge.net/projects/ini4j",
        mavenRepositoryUrl = "https://repo1.maven.org/maven2",
        groupId = "org.ini4j", artifactId = "ini4j",
        version = "0.5.4",
        authors = "Ivan Szkiba"
      ),

    LibraryLicense("intellij-markdown", libraryName = "jetbrains.markdown", url = "https://github.com/JetBrains/markdown")
      .apache("https://github.com/JetBrains/markdown/blob/master/LICENSE"),

    LibraryLicense("IntelliJ IDEA Code Coverage Agent", libraryName = "intellij-coverage", url = "https://github.com/jetbrains/intellij-coverage")
      .apache("https://github.com/JetBrains/intellij-coverage/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("IntelliJ IDEA Test Discovery Agent", libraryName = "intellij-test-discovery", url = "https://github.com/JetBrains/intellij-coverage/tree/master/test-discovery")
      .apache("https://github.com/JetBrains/intellij-coverage/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("ISO RELAX", libraryName = "isorelax", url = "https://sourceforge.net/projects/iso-relax/")
      .mit("https://sourceforge.net/projects/iso-relax/")
      .suppliedByPersons("Asami Tomoharu", "Murata Makoto", "Kohsuke Kawaguchi"),

    LibraryLicense("Jackson", libraryName = "jackson", url = "https://github.com/FasterXML/jackson")
      .apache("https://github.com/FasterXML/jackson-core/blob/2.14/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("jackson-jr-objects", libraryName = "jackson-jr-objects", url = "https://github.com/FasterXML/jackson-jr")
      .apache("https://github.com/FasterXML/jackson-jr/blob/2.16/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("Jackson Databind", libraryName = "jackson-databind", url = "https://github.com/FasterXML/jackson-databind")
      .apache("https://github.com/FasterXML/jackson-databind/blob/2.16/LICENSE")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("Jackson Dataformat CBOR", libraryName = "jackson-dataformat-cbor", url = "https://github.com/FasterXML/jackson-dataformats-binary")
      .apache("https://github.com/FasterXML/jackson-dataformats-binary/blob/2.14/pom.xml")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("Jackson Dataformat TOML", libraryName = "jackson-dataformat-toml", url = "https://github.com/FasterXML/jackson-dataformats-text")
      .apache("https://github.com/FasterXML/jackson-dataformats-text/blob/2.16/pom.xml")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("Jackson Dataformat YAML", libraryName = "jackson-dataformat-yaml", url = "https://github.com/FasterXML/jackson-dataformats-text")
      .apache("https://github.com/FasterXML/jackson-dataformats-text/blob/2.16/pom.xml")
      .suppliedByPersons("Tatu Saloranta", "Christopher Currie", "Paul Brown"),

    LibraryLicense("Jackson Module Kotlin", libraryName = "jackson-module-kotlin", url = "https://github.com/FasterXML/jackson-module-kotlin")
      .apache("https://github.com/FasterXML/jackson-module-kotlin/blob/2.16/LICENSE")
      .suppliedByPersons(
        "Tatu Saloranta", "Christopher Currie", "Paul Brown", "Jayson Minard",
        "Drew Stephens", "Vyacheslav Artemyev", "Dmitry Spikhalskiy"
      ),

    LibraryLicense("JaCoCo", libraryName = "JaCoCo", url = "https://www.eclemma.org/jacoco/")
      .eplV1("https://www.jacoco.org/jacoco/trunk/doc/license.html")
      .suppliedByOrganizations("Mountainminds GmbH & Co. KG and Contributors"),

    LibraryLicense("Jakarta ORO", libraryName = "OroMatcher", url = "https://jakarta.apache.org/oro/")
      .apache("https://svn.apache.org/repos/asf/jakarta/oro/trunk/LICENSE")
      .suppliedByPersons("Daniel Savarese", "Jon S. Stevens", "Takashi Okamoto", "Mark Murphy", "Michael Davey", "Harald Kuhn"),

    LibraryLicense("Jarchivelib", libraryName = "rauschig.jarchivelib", url = "https://github.com/thrau/jarchivelib")
      .apache("https://github.com/thrau/jarchivelib/blob/master/LICENSE"),

    LibraryLicense(libraryName = "Java Compatibility", url = "https://github.com/JetBrains/intellij-deps-java-compatibility")
      .gpl2ce("https://github.com/JetBrains/intellij-deps-java-compatibility/raw/master/LICENSE"),

    LibraryLicense("Java Poet", libraryName = "javapoet", url = "https://github.com/square/javapoet")
      .apache("https://github.com/square/javapoet/blob/master/LICENSE.txt"),

    LibraryLicense("Java Server Pages (JSP) for Visual Studio Code", version = "0.0.3", attachedTo = "intellij.textmate", url = "https://github.com/pthorsson/vscode-jsp")
      .mit("https://github.com/pthorsson/vscode-jsp/blob/master/LICENSE"),

    LibraryLicense("Java Simple Serial Connector", libraryName = "io.github.java.native.jssc", url = "https://github.com/java-native/jssc")
      .lgpl3("https://github.com/java-native/jssc/blob/master/LICENSE.txt"),

    LibraryLicense("Java String Similarity", libraryName = "java-string-similarity", url = "https://github.com/tdebatty/java-string-similarity")
      .mit("https://github.com/tdebatty/java-string-similarity/blob/master/LICENSE.md")
      .suppliedByPersons("Thibault Debatty"),

    LibraryLicense("JavaBeans Activation Framework", libraryName = "javax.activation", url = "https://github.com/javaee/activation")
      .cddl11("https://github.com/javaee/activation/blob/master/LICENSE.txt")
      .suppliedByPersons("Bill Shannon"),

    ffmpegLibraryLicense("javacpp-linux-x64"),
    ffmpegLibraryLicense("javacpp-macos-aarch64"),
    ffmpegLibraryLicense("javacpp-macos-x64"),
    ffmpegLibraryLicense("javacpp-windows-x64"),

    LibraryLicense("javawriter", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.android.core", url = "https://github.com/square/javawriter")
      .apache("https://github.com/square/javapoet/blob/master/LICENSE.txt"),

    LibraryLicense("javax inject", libraryName = "javax-inject", url = "https://github.com/javax-inject/javax-inject")
      .apache("https://github.com/javax-inject/javax-inject"),

    LibraryLicense("JAXB (Java Architecture for XML Binding) API", libraryName = "jaxb-api", url = "https://github.com/javaee/jaxb-spec")
      .cddl11("https://github.com/javaee/jaxb-spec/blob/master/LICENSE.txt")
      .suppliedByPersons("Roman Grigoriadi", "Martin Grebac", "Iaroslav Savytskyi"),

    LibraryLicense("JAXB (JSR 222) Reference Implementation", libraryName = "jaxb-runtime", url = "https://github.com/javaee/jaxb-v2")
      .cddl11("https://github.com/javaee/jaxb-v2/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.ECLIPSE),

    LibraryLicense(libraryName = "Jaxen", url = "https://github.com/jaxen-xpath/jaxen")
      .newBsd("https://github.com/jaxen-xpath/jaxen/blob/master/LICENSE.txt"),

    LibraryLicense("Jayway JsonPath", libraryName = "jsonpath", url = "https://github.com/json-path/JsonPath")
      .apache("https://github.com/json-path/JsonPath/blob/master/LICENSE"),

    LibraryLicense(libraryName = "jb-jdi", url = "https://github.com/JetBrains/intellij-deps-jdi")
      .gpl2ce("https://github.com/JetBrains/intellij-deps-jdi/raw/master/LICENSE.txt"),

    LibraryLicense("JCEF", libraryName = "jcef", url = "https://bitbucket.org/chromiumembedded/java-cef")
      .newBsd("https://bitbucket.org/chromiumembedded/java-cef/src/master/LICENSE.txt")
      .suppliedByPersons("Marshall A. Greenblatt"),

    LibraryLicense("JCIP Annotations", libraryName = "jcip", url = "https://jcip.net")
      .license("Creative Commons 2.5 Attribution", "https://creativecommons.org/licenses/by/2.5")
      .suppliedByPersons("Tim Peierls", "Brian Goetz"),

    LibraryLicense("JCodings", libraryName = "joni", transitiveDependency = true, version = "1.0.55", url = "https://github.com/jruby/jcodings")
      .mit("https://github.com/jruby/jcodings/blob/master/LICENSE.txt"),

    LibraryLicense("JDOM (JetBrains's fork)", version = "2", attachedTo = "intellij.platform.util.jdom", url = "https://github.com/JetBrains/intellij-deps-jdom/")
      .license("JDOM License", "https://github.com/JetBrains/intellij-deps-jdom/blob/master/LICENSE.txt")
      .forkedFrom(
        groupId = "org.jdom",
        artifactId = "jdom2",
        version = "2.0.6",
        mavenRepositoryUrl = "https://repo1.maven.org/maven2",
        sourceCodeUrl = "https://github.com/hunterhacker/jdom"
      ),

    LibraryLicense(libraryName = "jediterm-core", url = "https://github.com/JetBrains/jediterm")
      .lgpl3("https://github.com/JetBrains/jediterm/blob/master/LICENSE-LGPLv3.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "jediterm-ui", url = "https://github.com/JetBrains/jediterm")
      .lgpl3("https://github.com/JetBrains/jediterm/blob/master/LICENSE-LGPLv3.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("JetBrains Annotations", libraryName = "jetbrains-annotations", url = "https://github.com/JetBrains/java-annotations")
      .apache("https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt"),

    LibraryLicense("JetBrains Annotations for Java 5", libraryName = "jetbrains-annotations-java5", url = "https://github.com/JetBrains/java-annotations")
      .apache("https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt"),

    LibraryLicense("JetBrains Jewel IDE LaF Bridge", url = "https://github.com/JetBrains/jewel", libraryName = "jewel-ide-laf-bridge-243")
      .apache("https://github.com/JetBrains/jewel/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("JetBrains Jewel Markdown IDE LaF Bridge Styling", url = "https://github.com/JetBrains/jewel", libraryName = "jewel-markdown-ide-laf-bridge-styling-243")
      .apache("https://github.com/JetBrains/jewel/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("JetBrains Runtime", version = "21", attachedTo = "intellij.platform.ide.impl", url = "https://github.com/JetBrains/JetBrainsRuntime")
      .gpl2ce("https://github.com/JetBrains/JetBrainsRuntime/blob/master/LICENSE"),

    LibraryLicense("JetBrains Runtime API", libraryName = "jbr-api", url = "https://github.com/JetBrains/JetBrainsRuntime")
      .apache("https://github.com/JetBrains/JetBrainsRuntime/blob/main/LICENSE"),

    LibraryLicense("jetCheck", libraryName = "jetCheck", url = "https://github.com/JetBrains/jetCheck")
      .apache("https://github.com/JetBrains/jetCheck/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("JGit (Settings Sync and SettingsRepo)", libraryName = "jetbrains.intellij.deps.eclipse.jgit", url = "https://www.eclipse.org/jgit/")
      .license("Eclipse Distribution License 1.0", "https://www.eclipse.org/org/documents/edl-v10.php")
      .suppliedByOrganizations(Suppliers.ECLIPSE),

    LibraryLicense("JGit SSH Apache", libraryName = "eclipse.jgit.ssh.apache", url = "https://www.eclipse.org/jgit/")
      .license("Eclipse Distribution License 1.0", "https://www.eclipse.org/org/documents/edl-v10.php")
      .suppliedByOrganizations(Suppliers.ECLIPSE),

    LibraryLicense("JGit SSH Apache Agent", libraryName = "eclipse.jgit.ssh.apache.agent", url = "https://www.eclipse.org/jgit/")
      .license("Eclipse Distribution License 1.0", "https://www.eclipse.org/org/documents/edl-v10.php")
      .suppliedByOrganizations(Suppliers.ECLIPSE),

    LibraryLicense("JGoodies Common", libraryName = "jgoodies-common", url = "https://www.jgoodies.com/freeware/libraries/looks/")
      .newBsd("https://opensource.org/licenses/BSD-3-Clause"),  // no longer OSS; historic versions are still available

    LibraryLicense("JGoodies Forms", libraryName = "jgoodies-forms", url = "https://www.jgoodies.com/freeware/libraries/forms/")
      .newBsd("https://opensource.org/licenses/BSD-3-Clause"),  // no longer OSS; historic versions are still available

    LibraryLicense("Jing", libraryName = "jing", url = "https://relaxng.org/jclark/jing.html")
      .newBsd("https://opensource.org/license/bsd-3-clause/")
      .suppliedByOrganizations("Thai Open Source Software Center Ltd"),

    LibraryLicense(null, libraryName = "jline.terminal", url = "https://github.com/jline/jline3")
      .newBsd("https://github.com/jline/jline3/blob/master/LICENSE.txt")
      .suppliedByPersons("Guillaume Nodet"),

    LibraryLicense(null, libraryName = "jline.terminal.jansi", url = "https://github.com/jline/jline3")
      .newBsd("https://github.com/jline/jline3/blob/master/LICENSE.txt")
      .suppliedByPersons("Guillaume Nodet"),

    LibraryLicense(null, libraryName = "jline.terminal.jna", url = "https://github.com/jline/jline3")
      .newBsd("https://github.com/jline/jline3/blob/master/LICENSE.txt")
      .suppliedByPersons("Guillaume Nodet"),


    LibraryLicense("JNA", libraryName = "jna", url = "https://github.com/java-native-access/jna")
      .apache("https://github.com/java-native-access/jna/blob/master/LICENSE"),

    LibraryLicense("Joni", libraryName = "joni", url = "https://github.com/jruby/joni")
      .mit("https://github.com/jruby/joni/blob/master/LICENSE"),

    LibraryLicense("jps-javac-extension", libraryName = "jps-javac-extension", url = "https://github.com/JetBrains/jps-javac-extension/")
      .apache("https://github.com/JetBrains/jps-javac-extension/blob/master/LICENSE.txt"),

    LibraryLicense(libraryName = "jsch-agent-proxy", url = "https://github.com/ymnk/jsch-agent-proxy")
      .newBsd("https://github.com/ymnk/jsch-agent-proxy/blob/master/LICENSE.txt")
      .suppliedByPersons("Atsuhiko Yamanaka"),

    LibraryLicense("JSON", libraryName = "json.jar", version = LibraryLicense.CUSTOM_REVISION, url = "https://www.json.org/")
      .license("JSON License", "https://www.json.org/license.html"),

    LibraryLicense("JSON in Java", libraryName = "org.json:json", url = "https://github.com/stleary/JSON-java")
      .license("JSON License", "https://www.json.org/license.html"),

    LibraryLicense("JSON Schema (schema.json)", version = "draft-04", attachedTo = "intellij.json", url = "https://json-schema.org/draft-04/schema#")
      .newBsd("https://github.com/json-schema-org/json-schema-spec/blob/main/LICENSE"),

    LibraryLicense("JSON Schema (schema06.json)", version = "draft-06", attachedTo = "intellij.json", url = "https://json-schema.org/draft-06/schema#")
      .newBsd("https://github.com/json-schema-org/json-schema-spec/blob/main/LICENSE"),

    LibraryLicense("JSON Schema (schema07.json)", version = "draft-07", attachedTo = "intellij.json", url = "https://json-schema.org/draft-07/schema#")
      .newBsd("https://github.com/json-schema-org/json-schema-spec/blob/main/LICENSE"),

    LibraryLicense(libraryName = "jsoup", url = "https://jsoup.org")
      .mit("https://jsoup.org/license"),

    LibraryLicense(libraryName = "jsr305", url = "https://github.com/amaembo/jsr-305")
      .newBsd("https://github.com/amaembo/jsr-305/blob/master/ri/LICENSE")
      .suppliedByOrganizations("JSR305 expert group"),

    LibraryLicense(libraryName = "jsvg", url = "https://github.com/weisJ/jsvg")
      .mit("https://github.com/weisJ/jsvg/blob/master/LICENSE")
      .suppliedByPersons("Jannis Weis"),

    LibraryLicense("JUnit Pioneer", libraryName = "JUnit5Pioneer", url = "https://junit-pioneer.org")
      .eplV2("https://github.com/junit-pioneer/junit-pioneer/blob/main/LICENSE.md")
      .suppliedByPersons("Nicolai Parlog", "Matthias Bünger", "Simon Schrottner", "Mihály Verhás", "Daniel Kraus"),

    LibraryLicense(libraryName = "JUnit4", url = "https://junit.org/junit4/")
      .eplV1("https://junit.org/junit4/license.html")
      .suppliedByPersons("Marc Philipp", "David Saff", "Kevin Cooney", "Stefan Birkner"),

    LibraryLicense("JUnit5", libraryName = "JUnit5", url = "https://junit.org/junit5/")
      .eplV2("https://github.com/junit-team/junit5/blob/main/LICENSE.md")
      .suppliedByPersons("Marc Philipp", "David Saff", "Kevin Cooney", "Stefan Birkner"),

    LibraryLicense("JUnit5Jupiter", libraryName = "JUnit5Jupiter", url = "https://junit.org/junit5/")
      .eplV2("https://github.com/junit-team/junit5/blob/main/LICENSE.md"),

    LibraryLicense("JUnit5Launcher", libraryName = "JUnit5Launcher", url = "https://junit.org/junit5/")
      .eplV2("https://github.com/junit-team/junit5/blob/main/LICENSE.md"),

    LibraryLicense("JUnit5Params", libraryName = "JUnit5Params", url = "https://junit.org/junit5/")
      .eplV2("https://github.com/junit-team/junit5/blob/main/LICENSE.md"),

    LibraryLicense("JUnit5Vintage", libraryName = "JUnit5Vintage", url = "https://junit.org/junit5/")
      .eplV2("https://github.com/junit-team/junit5/blob/main/LICENSE.md"),

    LibraryLicense(libraryName = "jzlib", url = "http://www.jcraft.com/jzlib/")
      .newBsd("https://github.com/ymnk/jzlib/raw/master/LICENSE.txt"),

    LibraryLicense("kaml", libraryName = "kaml", url = "https://github.com/charleskorn/kaml")
      .apache("https://github.com/charleskorn/kaml/blob/main/LICENSE")
      .suppliedByPersons("Charles Korn"),

    LibraryLicense("Kconfig for the Zephyr Project", version = "1.2.0", attachedTo = "intellij.textmate", url = "https://github.com/trond-snekvik/vscode-kconfig")
      .mit("https://github.com/trond-snekvik/vscode-kconfig/blob/master/LICENSE"),

    LibraryLicense("KInference", libraryName = "kinference.core", url = "https://packages.jetbrains.team/maven/p/ki/maven")
      .apache("https://github.com/JetBrains-Research/kinference/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kodein-DI", libraryName = "kodein-di-jvm", url = "https://github.com/kosi-libs/Kodein")
      .mit("https://github.com/kosi-libs/Kodein/blob/master/LICENSE.txt"),

    LibraryLicense("kotlin-metadata", libraryName = "kotlin-metadata", url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin Coroutines for Guava", libraryName = "kotlinx-coroutines-guava", url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin Coroutines for JDK 8", libraryName = "kotlinx-coroutines-core", url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin Coroutines for Slf4j", libraryName = "kotlinx-coroutines-slf4j", url = "https://github.com/Kotlin/kotlinx.coroutines")
      .apache("https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin library providing basic IO primitives", libraryName = "kotlinx-io-core", url = "https://github.com/Kotlin/kotlinx-io")
      .apache("https://github.com/Kotlin/kotlinx-io/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(name = "Kotlin Logging", libraryName = "io.github.oshai.kotlin.logging.jvm", url = "https://github.com/oshai/kotlin-logging/")
      .apache("https://github.com/oshai/kotlin-logging/blob/master/LICENSE")
      .suppliedByPersons("Ohad Shai"),

    LibraryLicense("Kotlin multiplatform / multi-format serialization", libraryName = "kotlinx-serialization-core", url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin multiplatform / multi-format serialization", libraryName = "kotlinx-serialization-json", url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin multiplatform / multi-format serialization", libraryName = "kotlinx-serialization-json-io", url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin multiplatform / multi-format serialization", libraryName = "kotlinx-serialization-protobuf", url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin multiplatform / multi-format serialization", libraryName = "kotlinx-serialization-cbor", url = "https://github.com/Kotlin/kotlinx.serialization")
      .apache("https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin reflection library", libraryName = "kotlin-reflect", url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kotlin Standard Library", libraryName = "kotlin-stdlib", url = "https://github.com/JetBrains/kotlin")
      .apache("https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("kotlinx-datetime-jvm", libraryName = "kotlinx-datetime-jvm", url = "https://github.com/Kotlin/kotlinx-datetime")
      .apache("https://github.com/Kotlin/kotlinx-datetime/blob/master/LICENSE.txt")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("kotlinx-document-store-mvstore", libraryName = "kotlinx-document-store-mvstore", url = "https://github.com/lamba92/kotlin.document.store")
      .apache("https://github.com/lamba92/kotlin.document.store/blob/master/LICENSE"),

    LibraryLicense("kotlinx.html", libraryName = "kotlinx-html-jvm", url = "https://github.com/Kotlin/kotlinx.html")
      .apache("https://github.com/Kotlin/kotlinx.html/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Kryo5", libraryName = "Kryo5", url = "https://github.com/EsotericSoftware/kryo")
      .newBsd("https://github.com/EsotericSoftware/kryo/blob/master/LICENSE.md")
      .suppliedByPersons("Nathan Sweet"),

    LibraryLicense(libraryName = "ktor-client-auth", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-cio", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-cio-internal", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-content-negotiation", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-encoding", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-java", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-client-logging", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-serialization-gson", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense(libraryName = "ktor-serialization-kotlinx-json", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("ktor.io TLS", libraryName = "ktor-network-tls", url = "https://github.com/ktorio/ktor")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Ktor Client Core", libraryName = "ktor-client-core", url = "https://github.com/ktorio/ktor/tree/main/ktor-client/ktor-client-core")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("Ktor Client OkHttp", libraryName = "ktor-client-okhttp", url = "https://github.com/ktorio/ktor/tree/main/ktor-client/ktor-client-okhttp")
      .apache("https://github.com/ktorio/ktor/blob/main/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("kXML2", libraryName = "kxml2", url = "https://github.com/kobjects/kxml2/")
      .simplifiedBsd("https://github.com/kobjects/kxml2/blob/master/license.txt"),

    LibraryLicense("Language Tool (JetBrains's fork)", libraryName = "org.jetbrains.intellij.deps.languagetool:languagetool-core", url = "https://github.com/JetBrains/languagetool")
      .lgpl21("https://github.com/JetBrains/languagetool/blob/master/COPYING.txt")
      .forkedFrom(
        groupId = "org.languagetool",
        artifactId = "languagetool-core",
        revision = "5c6be17808cee3edc84ce53df97236521f8a8f7e",
        sourceCodeUrl = "https://github.com/languagetool-org/languagetool"
      )
      .suppliedByPersons("Daniel Naber", "Marcin Miłkowski"),

    LibraryLicense("Language Tool (JetBrains's fork, English)", libraryName = "org.jetbrains.intellij.deps.languagetool:language-en", url = "https://github.com/JetBrains/languagetool")
      .lgpl21("https://github.com/JetBrains/languagetool/blob/master/COPYING.txt")
      .forkedFrom(
        groupId = "org.languagetool",
        artifactId = "language-en",
        revision = "5c6be17808cee3edc84ce53df97236521f8a8f7e",
        sourceCodeUrl = "https://github.com/languagetool-org/languagetool"
      )
      .suppliedByPersons("Daniel Naber", "Marcin Miłkowski"),

    LibraryLicense("Log4j", libraryName = "Log4J", url = "https://www.slf4j.org/legacy.html#log4j-over-slf4j")
      .apache("https://github.com/qos-ch/slf4j/blob/master/log4j-over-slf4j/LICENSE.txt")
      .suppliedByOrganizations("QOS.ch Sarl"),

    LibraryLicense("LWJGL", libraryName="org.lwjgl.lwjgl", url = "https://github.com/LWJGL/lwjgl3")
      .newBsd("https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md")
      .additionalLibraryNames(
        "org.lwjgl.lwjgl.tinyfd"
      ),

    LibraryLicense("lz4-java", libraryName = "lz4-java", url = "https://github.com/lz4/lz4-java")
      .apache("https://github.com/lz4/lz4-java/blob/master/LICENSE.txt"),

    LibraryLicense("MathJax", version = "2.6.1", attachedTo = "intellij.python", url = "https://github.com/mathjax/MathJax")
      .apache("https://github.com/mathjax/MathJax/blob/master/LICENSE"),

    LibraryLicense("Maven archetype catalog", libraryName = "apache.maven.archetype.catalog-no-trans:321", url = "https://maven.apache.org/archetype/archetype-common/index.html")
      .apache("https://github.com/apache/maven-archetype"),

    LibraryLicense("Maven archetype common", libraryName = "apache.maven.archetype.common-no-trans:3.2.1", url = "https://maven.apache.org/archetype/archetype-common/index.html")
      .apache("https://github.com/apache/maven-archetype"),

    LibraryLicense("Maven core", libraryName = "apache.maven.core:3.8.3", url = "https://maven.apache.org/ref/3.8.6/maven-core/")
      .apache("https://github.com/apache/maven/blob/master/LICENSE"),

    LibraryLicense("Maven indexer", libraryName = "jetbrains.idea.maven.indexer.api.rt", url = "https://maven.apache.org/maven-indexer/indexer-core/index.html")
      .apache("https://github.com/apache/maven-indexer")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Maven Resolver Provider", libraryName = "maven-resolver-provider", url = "https://maven.apache.org/ref/3.6.1/maven-resolver-provider/")
      .additionalLibraryNames(
        "org.apache.maven.resolver:maven-resolver-connector-basic",
        "org.apache.maven.resolver:maven-resolver-transport-http",
        "org.apache.maven.resolver:maven-resolver-transport-file"
      )
      .apache("https://github.com/apache/maven-resolver/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Maven wagon provider api", libraryName = "apache.maven.wagon.provider.api:3.5.2", url = "https://maven.apache.org/wagon/wagon-provider-api/index.html")
      .apache("https://github.com/apache/maven-wagon")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Maven Wrapper", libraryName = "io.takari.maven.wrapper", url = "https://github.com/takari/maven-wrapper")
      .apache("https://github.com/takari/maven-wrapper/blob/master/LICENSE.txt"),

    LibraryLicense("Maven3", version = "3.6.1", attachedTo = "intellij.maven.server.m3.common", url = "https://maven.apache.org/")
      .additionalLibraryNames(
        "org.apache.maven.shared:maven-dependency-tree:1.2",
        "org.apache.maven.archetype:archetype-common:2.2"
      )
      .apache("https://github.com/apache/maven/blob/master/LICENSE"),

    LibraryLicense("MDX for Visual Studio Code", version = "1.8.7", attachedTo = "intellij.textmate", url = "https://github.com/mdx-js/mdx-analyzer/tree/main/packages/vscode-mdx")
      .mit("https://github.com/mdx-js/mdx-analyzer/blob/main/packages/vscode-mdx/LICENSE"),

    LibraryLicense("Memory File System", libraryName = "memoryfilesystem", url = "https://github.com/marschall/memoryfilesystem")
      .mit("https://github.com/marschall/memoryfilesystem#faq"),

    LibraryLicense("mercurial_prompthooks", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.vcs.hg", url = "https://github.com/willemv/mercurial_prompthooks")
      .license("GPLv2 (used as hg extension called from hg executable)", "https://github.com/willemv/mercurial_prompthooks/blob/master/LICENSE.txt"),

    LibraryLicense("microba", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.libraries.microba", url = "https://microba.sourceforge.net/")
      .newBsd("https://microba.sourceforge.net/license.txt")
      .suppliedByPersons("Michael Baranov"),

    LibraryLicense("MigLayout", libraryName = "miglayout-swing", url = "https://github.com/mikaelgrev/miglayout/")
      .newBsd("https://github.com/mikaelgrev/miglayout/blob/master/src/site/resources/docs/license.txt")
      .suppliedByOrganizations("MiG InfoCom AB"),

    LibraryLicense("morfologik-fsa", libraryName = "org.carrot2:morfologik-fsa", url = "https://github.com/morfologik/morfologik-stemming")
      .newBsd("https://github.com/morfologik/morfologik-stemming/blob/master/LICENSE.txt")
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),

    LibraryLicense("morfologik-fsa-builders", libraryName = "org.carrot2:morfologik-fsa-builders", url = "https://github.com/morfologik/morfologik-stemming")
      .newBsd("https://github.com/morfologik/morfologik-stemming/blob/master/LICENSE.txt")
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),

    LibraryLicense("morfologik-speller", libraryName = "org.carrot2:morfologik-speller", url = "https://github.com/morfologik/morfologik-stemming")
      .newBsd("https://github.com/morfologik/morfologik-stemming/blob/master/LICENSE.txt")
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),

    LibraryLicense("morfologik-stemming", libraryName = "org.carrot2:morfologik-stemming", url = "https://github.com/morfologik/morfologik-stemming")
      .newBsd("https://github.com/morfologik/morfologik-stemming/blob/master/LICENSE.txt")
      .suppliedByPersons("Dawid Weiss", "Marcin Miłkowski"),

    LibraryLicense(libraryName = "mvstore", url = "https://github.com/h2database/h2database")
      .eplV1("https://github.com/h2database/h2database/blob/master/LICENSE.txt"),

    LibraryLicense("NanoXML", version = "2.2.3", attachedTo = "intellij.platform.util.nanoxml", url = "https://central.sonatype.com/artifact/be.cyberelf.nanoxml/nanoxml/2.2.3")
      .license("zlib/libpng", "https://github.com/saulhidalgoaular/nanoxml/raw/master/LICENSE.txt")
      .suppliedByPersons("Marc De Scheemaecker", "Saul Hidalgo"),

    LibraryLicense("nest_asyncio", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.python.community.impl", url = "https://github.com/erdewit/nest_asyncio")
      .simplifiedBsd("https://github.com/erdewit/nest_asyncio/blob/master/LICENSE"),

    LibraryLicense("net.loomchild.segment", libraryName = "net.loomchild:segment:2.0.1", url = "https://github.com/loomchild/segment")
      .mit("https://github.com/loomchild/segment/blob/master/LICENSE.txt")
      .suppliedByPersons("Jarek Lipski"),

    netty("netty"),
    netty("netty-buffer"),
    netty("netty-codec-compression"),
    netty("netty-codec-http"),
    netty("netty-codec-protobuf"),
    netty("netty-handler-proxy"),
    netty("netty-tcnative-boringssl"),

    LibraryLicense(libraryName = "ngram-slp", url = "https://github.com/SLP-team/SLP-Core")
      .mit("https://github.com/SLP-team/SLP-Core/blob/master/LICENSE")
      .suppliedByOrganizations("SLP-team"),

    LibraryLicense("Objenesis", libraryName = "Objenesis", url = "https://objenesis.org/")
      .apache("https://github.com/easymock/objenesis/blob/master/LICENSE.txt")
      .suppliedByPersons("Henri Tremblay", "Joe Walnes", "Leonardo Mesquita"),

    LibraryLicense("OkHttp", libraryName = "okhttp", url = "https://square.github.io/okhttp/")
      .apache("https://square.github.io/okhttp/#license"),

    LibraryLicense(libraryName = "opentelemetry", url = "https://opentelemetry.io/")
      .apache("https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE")
      .suppliedByOrganizations("The OpenTelemetry Authors"),

    LibraryLicense(libraryName = "opentelemetry-exporter-otlp", url = "https://opentelemetry.io/")
      .apache("https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE")
      .suppliedByOrganizations("The OpenTelemetry Authors"),

    LibraryLicense(libraryName = "opentelemetry-exporter-otlp-common", url = "https://opentelemetry.io/")
      .apache("https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE")
      .suppliedByOrganizations("The OpenTelemetry Authors"),

    LibraryLicense(libraryName = "opentelemetry-extension-kotlin", url = "https://opentelemetry.io/")
      .apache("https://github.com/open-telemetry/opentelemetry-java/blob/main/LICENSE")
      .suppliedByOrganizations("The OpenTelemetry Authors"),

    LibraryLicense(libraryName = "opentelemetry-semconv", url = "https://opentelemetry.io/")
      .apache("https://github.com/open-telemetry/semantic-conventions-java/blob/main/LICENSE")
      .suppliedByOrganizations("The OpenTelemetry Authors"),

    LibraryLicense(libraryName = "opentest4j", url = "https://github.com/ota4j-team/opentest4j")
      .apache("https://github.com/ota4j-team/opentest4j/blob/master/LICENSE"),

    LibraryLicense("OverlayScrollbars", version = "2.1.1", attachedTo = "intellij.idea.community.main", url = "https://kingsora.github.io/OverlayScrollbars")
      .mit("https://github.com/KingSora/OverlayScrollbars/blob/master/LICENSE"),

    LibraryLicense("Package Search API-Client", libraryName = "package-search-api-client", url = "https://github.com/JetBrains/package-search-api-models")
      .apache("https://github.com/JetBrains/package-search-api-models/blob/master/LICENSE")
      .suppliedByOrganizations("JetBrains Team"),

    LibraryLicense("pip", version = "24.3.1", attachedTo = "intellij.python", url = "https://pip.pypa.io/")
      .mit("https://github.com/pypa/pip/blob/main/LICENSE.txt"),

    LibraryLicense("plexus-archiver", libraryName = "plexus-archiver", url = "https://github.com/codehaus-plexus/plexus-archiver")
      .apache("https://github.com/codehaus-plexus/plexus-archiver/blob/master/LICENSE")
      .suppliedByOrganizations("The Codehaus Foundation, Inc."),

    LibraryLicense("Plexus Utils", libraryName = "plexus-utils", url = "https://github.com/codehaus-plexus/plexus-utils")
      .apache("https://github.com/codehaus-plexus/plexus-utils/blob/master/LICENSE.txt")
      .suppliedByOrganizations("The Codehaus Foundation, Inc."),

    LibraryLicense("PLY", version = "3.7", attachedTo = "intellij.python", url = "https://www.dabeaz.com/ply/")
      .newBsd("https://github.com/dabeaz/ply/blob/master/src/ply/lex.py"),

    LibraryLicense(libraryName = "pngencoder", url = "https://github.com/pngencoder/pngencoder")
      .mit("https://github.com/pngencoder/pngencoder/blob/develop/LICENSE"),

    LibraryLicense("pockets", version = "0.9.1", attachedTo = "intellij.python", url = "https://pockets.readthedocs.io/")
      .newBsd("https://github.com/RobRuana/pockets/blob/master/LICENSE"),

    LibraryLicense("Protocol Buffers", libraryName = "protobuf", url = "https://developers.google.com/protocol-buffers")
      .newBsd("https://github.com/google/protobuf/blob/master/LICENSE"),

    LibraryLicense("Proxy Vole", libraryName = "proxy-vole", url = "https://github.com/akuhtz/proxy-vole")
      .apache("https://github.com/akuhtz/proxy-vole/blob/master/LICENSE.md"),

    LibraryLicense("pty4j", libraryName = "pty4j", url = "https://github.com/JetBrains/pty4j")
      .eplV1("https://github.com/JetBrains/pty4j/blob/master/LICENSE"),

    LibraryLicense("pycodestyle", version = "2.8.0", attachedTo = "intellij.python", url = "https://pycodestyle.pycqa.org/")
      .mit("https://github.com/PyCQA/pycodestyle/blob/main/LICENSE"),

    LibraryLicense("pyparsing", version = "1.5.6", attachedTo = "intellij.python", url = "https://github.com/pyparsing/pyparsing/")
      .mit("https://github.com/pyparsing/pyparsing/blob/master/LICENSE"),

    LibraryLicense("qdox-java-parser", libraryName = "qdox-java-parser", url = "https://github.com/paul-hammant/qdox")
      .apache("https://github.com/paul-hammant/qdox/blob/master/LICENSE.txt"),

    LibraryLicense("rd core", libraryName = "rd-core", url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-core")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),

    LibraryLicense("rd framework", libraryName = "rd-framework", url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-framework")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("rd generator", libraryName = "rd-gen", url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-gen")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE")
      .suppliedByOrganizations(Suppliers.JETBRAINS),

    LibraryLicense("rd Swing integration", libraryName = "rd-swing", url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-swing")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),

    LibraryLicense("rd text buffers", libraryName = "rd-text", url = "https://github.com/JetBrains/rd/tree/master/rd-kt/rd-text")
      .apache("https://github.com/JetBrains/rd/blob/master/LICENSE"),

    LibraryLicense("Reactive Streams", libraryName = "reactivestreams.reactive.streams", url = "https://github.com/reactive-streams/reactive-streams-jvm")
      .mit("https://github.com/reactive-streams/reactive-streams-jvm/blob/master/LICENSE"),

    LibraryLicense("Relax NG Object Model", libraryName = "rngom-20051226-patched.jar", url = "https://github.com/kohsuke/rngom", version = LibraryLicense.CUSTOM_REVISION)
      .mit("https://github.com/kohsuke/rngom/blob/master/licenceheader.txt"),

    LibraryLicense("Rhino JavaScript Engine", libraryName = "rhino", url = "https://github.com/mozilla/rhino")
      .mpl2("https://github.com/mozilla/rhino/blob/master/LICENSE.txt"),

    LibraryLicense("Roboto", version = "1.100141", attachedTo = "intellij.platform.resources", url = "https://github.com/googlefonts/roboto")
      .apache("https://github.com/google/roboto/blob/master/LICENSE"),

    LibraryLicense("roman", version = "1.4.0", attachedTo = "intellij.python", url = "https://docutils.sourceforge.io/docutils/utils/roman.py")
      .license("Python 2.1.1 license", "https://www.python.org/download/releases/2.1.1/license/"),

    LibraryLicense(libraryName = "sa-jdwp", url = "https://github.com/JetBrains/jdk-sa-jdwp")
      .gpl2ce("https://github.com/JetBrains/jdk-sa-jdwp/raw/master/LICENSE.txt"),

    LibraryLicense(libraryName = "Saxon-6.5.5", version = "6.5.5", url = "https://saxon.sourceforge.net/")
      .license("MPL 1.1", "https://www.mozilla.org/en-US/MPL/1.1/"),

    LibraryLicense(libraryName = "Saxon-9HE", version = "9.9", url = "https://saxon.sourceforge.net/")
      .mpl2("https://www.mozilla.org/en-US/MPL/2.0/"),

    LibraryLicense(name = "Schema Kenerator", libraryName = "io.github.smiley4.schema.kenerator.core", url = "https://github.com/SMILEY4/schema-kenerator",
                   additionalLibraryNames = listOf(
                     "io.github.smiley4.schema.kenerator.jsonschema",
                     "io.github.smiley4.schema.kenerator.serialization",))
      .apache("https://github.com/SMILEY4/schema-kenerator/blob/develop/LICENSE"),

    LibraryLicense("setuptools", version = "44.1.1", attachedTo = "intellij.python", url = "https://setuptools.pypa.io/")
      .mit("https://github.com/pypa/setuptools/blob/main/LICENSE"),

    LibraryLicense("six.py", version = "1.9.0", attachedTo = "intellij.python", url = "https://six.readthedocs.io/")
      .mit("https://github.com/benjaminp/six/blob/master/LICENSE"),

    LibraryLicense("Skiko", libraryName = "jetbrains.skiko.awt.compose", url = "https://github.com/JetBrains/skiko/")
      .apache("https://github.com/JetBrains/skiko/blob/master/LICENSE"),

    LibraryLicense(
      name = "Skiko Runtime",
      libraryName = "jetbrains.skiko.awt.runtime.all",
      additionalLibraryNames = listOf("jetbrains.skiko.awt.runtime.all.0.8.18"),
      url = "https://github.com/JetBrains/skiko/"
    ).apache("https://github.com/JetBrains/skiko/blob/master/LICENSE"),

    LibraryLicense(libraryName = "slf4j-api", url = "https://slf4j.org/")
      .mit("https://www.slf4j.org/license.html")
      .suppliedByOrganizations("QOS.ch Sarl"),

    LibraryLicense(libraryName = "slf4j-jdk14", url = "https://slf4j.org/")
      .mit("https://www.slf4j.org/license.html")
      .suppliedByOrganizations("QOS.ch Sarl"),

    LibraryLicense("SnakeYAML", libraryName = "snakeyaml", url = "https://bitbucket.org/snakeyaml/snakeyaml/")
      .apache("https://bitbucket.org/snakeyaml/snakeyaml/src/master/LICENSE.txt")
      .suppliedByPersons("Andrey Somov", "Alexander Maslov", "Jordan Angold"),

    LibraryLicense("snakeyaml-engine", libraryName = "snakeyaml-engine", url = "https://bitbucket.org/snakeyaml/snakeyaml-engine/")
      .apache("https://bitbucket.org/snakeyaml/snakeyaml-engine/src/master/LICENSE.txt")
      .suppliedByPersons("Andrey Somov", "Alexander Maslov"),

    LibraryLicense("Sonatype Nexus: Indexer", version = "3.0.4", attachedTo = "intellij.maven.server.m3.common", url = "https://central.sonatype.com/artifact/org.sonatype.nexus/nexus-indexer")
      .additionalLibraryNames(
        "org.sonatype.nexus:nexus-indexer:3.0.4",
        "org.sonatype.nexus:nexus-indexer-artifact:1.0.1"
      )
      .eplV1("https://central.sonatype.com/artifact/org.sonatype.nexus/nexus-indexer"),

    LibraryLicense("SourceCodePro", version = "2.010", attachedTo = "intellij.platform.resources", url = "https://github.com/adobe-fonts/source-code-pro")
      .license("OFL", "https://github.com/adobe-fonts/source-code-pro/blob/master/LICENSE.md"),

    LibraryLicense("sphinxcontrib-napoleon", version = "0.7", attachedTo = "intellij.python", url = "https://sphinxcontrib-napoleon.readthedocs.io/")
      .simplifiedBsd("https://github.com/sphinx-contrib/napoleon/blob/master/LICENSE"),

    LibraryLicense("Squareup Okio", libraryName = "squareup.okio.jvm", url = "https://github.com/square/okio")
      .apache("https://github.com/square/okio/blob/master/LICENSE.txt")
      .suppliedByOrganizations("Square, Inc."),

    LibraryLicense("Squareup Wire", libraryName = "squareup.wire.runtime.jvm", url = "https://github.com/square/wire")
      .apache("https://github.com/square/wire/blob/master/LICENSE.txt")
      .suppliedByOrganizations("Square, Inc."),

    LibraryLicense("ssh-nio-fs", libraryName = "ssh-nio-fs", url = "https://github.com/JetBrains/intellij-deps-ssh-nio-fs")
      .mit("https://github.com/JetBrains/intellij-deps-ssh-nio-fs/blob/master/LICENSE")
      .forkedFrom(
        sourceCodeUrl = "https://github.com/lucastheisen/jsch-nio",
        mavenRepositoryUrl = "https://repo1.maven.org/maven2",
        groupId = "com.pastdev", artifactId = "jsch-nio",
        version = "1.0.14",
        authors = "Lucas Theisen"
      ),

    LibraryLicense("StreamEx", libraryName = "StreamEx", url = "https://github.com/amaembo/streamex")
      .apache("https://github.com/amaembo/streamex/blob/master/LICENSE"),

    LibraryLicense("swingx", libraryName = "swingx", url = "https://central.sonatype.com/artifact/org.swinglabs/swingx-core/1.6.2-2")
      .lgpl21("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html")
      .suppliedByOrganizations("Sun Microsystems, Inc."),

    LibraryLicense("Testcontainers Core", libraryName = "testcontainers", url = "https://java.testcontainers.org")
      .mit("https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE")
      .suppliedByPersons("Richard North"),

    LibraryLicense(libraryName = "TestNG", url = "https://testng.org/")
      .apache("https://github.com/cbeust/testng/blob/master/LICENSE.txt"),

    LibraryLicense("The Erlang LS extension for VSCode", version = "0.0.43", attachedTo = "intellij.textmate", url = "https://github.com/mblode/vscode-twig-language-2")
      .apache("https://github.com/erlang-ls/vscode/blob/main/LICENSE.md"),

    LibraryLicense("Thrift", libraryName = "libthrift", url = "https://thrift.apache.org/")
      .apache("https://github.com/apache/thrift/blob/master/LICENSE"),

    LibraryLicense("thriftpy2", version = "0.4.13", attachedTo = "intellij.python", url = "https://github.com/Thriftpy/thriftpy2/")
      .mit("https://github.com/Thriftpy/thriftpy2/blob/master/LICENSE"),

    // for traceprocessor-proto module library in intellij.android.profilersAndroid
    LibraryLicense("Trang", libraryName = "trang-core.jar", version = LibraryLicense.CUSTOM_REVISION, url = "https://relaxng.org/jclark/trang.html")
      .newBsd("https://opensource.org/license/bsd-3-clause/"),

    LibraryLicense("Trove4j (JetBrains's fork)", libraryName = "trove", url = "https://github.com/JetBrains/intellij-deps-trove4j")
      .lgpl21("https://github.com/JetBrains/intellij-deps-trove4j/blob/master/LICENSE.txt")
      .forkedFrom(
        sourceCodeUrl = "https://sourceforge.net/p/trove4j/cvs",
        groupId = "net.sf.trove4j",
        artifactId = "trove4j"
      ),

    LibraryLicense("Typeshed", version = LibraryLicense.CUSTOM_REVISION, attachedTo = "intellij.python", url = "https://github.com/python/typeshed")
      .apache("https://github.com/python/typeshed/blob/master/LICENSE"),

    LibraryLicense("unit-api", libraryName = "javax.measure:unit-api:1.0", url = "https://github.com/unitsofmeasurement/unit-api")
      .newBsd("https://github.com/unitsofmeasurement/unit-api/blob/master/LICENSE")
      .suppliedByPersons(
        "Jean-Marie Dautelle", "Werner Keil", "Otávio Gonçalves de Santana",
        "Martin Desruisseaux", "Thodoris Bais", "Daniel Dias", "Jacob Glickman",
        "Magesh Kasthuri", "Chris Senior", "Leonardo de Moura Rocha Lima", "Almas Shaikh",
        "Karen Legrand", "Rajmahendra Hegde", "Mohamed Mahmoud Taman", "Werner Keil",
        "Mohammed Al-Moayed", "Werner Keil"
      ),

    LibraryLicense("uom-lib-common", libraryName = "tech.uom.lib:uom-lib-common:1.1", url = "https://github.com/unitsofmeasurement/uom-lib")
      .newBsd("https://github.com/unitsofmeasurement/uom-lib/blob/master/LICENSE")
      .suppliedByPersons("Jean-Marie Dautelle", "Werner Keil"),

    LibraryLicense(name = "vavr", libraryName = "vavr", url = "https://vavr.io/")
      .mit("https://github.com/vavr-io/vavr/blob/master/LICENSE")
      .suppliedByPersons("Daniel Dietrich"),

    LibraryLicense(libraryName = "Velocity", url = "https://velocity.apache.org/")
      .suppliedByOrganizations(Suppliers.APACHE)
      .apache("https://github.com/apache/velocity-engine/blob/master/LICENSE"),

    LibraryLicense("Vim Script language support for Atom", version = "1.2.1", attachedTo = "intellij.textmate", url = "https://github.com/AlexPl292/language-viml")
      .mit("https://github.com/AlexPl292/language-viml/blob/master/LICENSE.txt"),

    LibraryLicense("virtualenv", version = "20.13.0", attachedTo = "intellij.python", url = "https://virtualenv.pypa.io/")
      .mit("https://github.com/pypa/virtualenv/blob/main/LICENSE"),

    LibraryLicense("Visual Studio Code", version = "1.90.0", attachedTo = "intellij.textmate", url = "https://github.com/Microsoft/vscode/")
      .mit("https://github.com/Microsoft/vscode-react-native/blob/master/LICENSE.txt"),

    LibraryLicense("VS Code Twig Language 2", version = "0.9.4", attachedTo = "intellij.textmate", url = "https://github.com/mblode/vscode-twig-language-2")
      .mit("https://github.com/mblode/vscode-twig-language-2/blob/master/LICENSE.md"),

    // originally https://github.com/pelotoncycle/weberknecht
    LibraryLicense("weberknecht", libraryName = "weberknecht-0.1.5.jar", version = "0.1.5", url = "https://github.com/pusher-community/titanium_pusher_android/blob/master/src/de/roderick/weberknecht/")
      .apache("https://github.com/pusher-community/titanium_pusher_android/blob/master/src/de/roderick/weberknecht/WebSocket.java"),

    LibraryLicense(libraryName = "winp", url = "https://github.com/jenkinsci/winp")
      .mit("https://github.com/jenkinsci/winp/blob/master/LICENSE.txt")
      .suppliedByPersons("Kohsuke Kawaguchi"),

    // for workmanager-inspector-proto module library in intellij.android.app-inspection.inspectors.workmanager.model
    LibraryLicense("Xalan", libraryName = "Xalan-2.7.3", url = "https://xalan.apache.org/xalan-j/")
      .apache("https://xalan.apache.org/xalan-j/#license")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Xalan serializer", libraryName = "Serializer-2.7.3", url = "https://xalan.apache.org/xalan-j/")
      .apache("https://xalan.apache.org/xalan-j/#license")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense(libraryName = "Xerces", url = "https://xerces.apache.org/xerces2-j/")
      .apache("https://svn.apache.org/repos/asf/xerces/java/trunk/LICENSE")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("Xerial SQLite JDBC", libraryName = "sqlite", url = "https://github.com/xerial/sqlite-jdbc")
      .apache("https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE")
      .suppliedByOrganizations("Xerial Project"),

    LibraryLicense("Xerial SQLite JDBC", libraryName = "sqlite-native", url = "https://github.com/xerial/sqlite-jdbc")
      .apache("https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE")
      .suppliedByOrganizations("Xerial Project"),

    LibraryLicense("xml-apis-ext", libraryName = "xml-apis-ext", url = "https://xerces.apache.org/xml-commons/components/external/")
      .apache("https://svn.apache.org/viewvc/xerces/xml-commons/trunk/java/external/LICENSE?view=markup")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("xml-resolver", libraryName = "xml-resolver", url = "https://xerces.apache.org/xml-commons/components/resolver/")
      .apache("https://svn.apache.org/viewvc/xerces/xml-commons/trunk/LICENSE?view=markup")
      .suppliedByOrganizations(Suppliers.APACHE),

    LibraryLicense("XMLBeans", libraryName = "XmlBeans", url = "https://xmlbeans.apache.org/")
      .apache("https://svn.jetbrains.org/idea/Trunk/bundled/WebServices/resources/lib/xmlbeans-2.3.0/xmlbeans.LICENSE")
      .suppliedByPersons("Cezar Andrei", "Radu Preotiuc", "Radu Preotiuc", "Wing Yew Poon", "Jacob Danner", "POI Team"),

    LibraryLicense("XmlRPC", libraryName = "XmlRPC", url = "https://ws.apache.org/xmlrpc/xmlrpc2/")
      .apache("https://ws.apache.org/xmlrpc/xmlrpc2/license.html")
      .suppliedByPersons(
        "Daniel Rall", "Jon Scott Stevens", "John Wilson",
        "Jochen Wiedmann", "Jason van Zyl", "Siegfried Goeschl",
        "Andrew Evers", "Henri Gomez", "Ryan Hoegg",
        "Leonard Richarson", "Hannes Wallnoefer"
      ),

    LibraryLicense("XStream", libraryName = "XStream", url = "https://x-stream.github.io/")
      .newBsd("https://x-stream.github.io/license.html")
      .suppliedByOrganizations("XStream Committers"),

    LibraryLicense("XZ for Java", libraryName = "xz", url = "https://tukaani.org/xz/java.html")
      .public("https://git.tukaani.org/?p=xz-java.git;a=blob;f=COPYING;h=8dd17645c4610c3d5eed9bcdd2699ecfac00406b;hb=refs/heads/master"),

    LibraryLicense("zip-signer", libraryName = "zip-signer", url = "https://github.com/JetBrains/marketplace-zip-signer")
      .apache("https://github.com/JetBrains/marketplace-zip-signer/blob/master/LICENSE"),

    LibraryLicense("zstd-jni", libraryName = "zstd-jni", url = "https://github.com/luben/zstd-jni")
      .simplifiedBsd("https://github.com/luben/zstd-jni/blob/master/LICENSE"),

    jetbrainsLibrary("ai.grazie.emb"),
    jetbrainsLibrary("ai.grazie.nlp.detect"),
    jetbrainsLibrary("ai.grazie.nlp.encoder.bert.uncased"),
    jetbrainsLibrary("ai.grazie.spell.gec.engine.local"),
    jetbrainsLibrary("ai.grazie.spell.hunspell.en"),
    jetbrainsLibrary("ai.grazie.utils.lucene.lt.compatibility"),
    jetbrainsLibrary("cloud-config-client"),
    jetbrainsLibrary("com.jetbrains.fus.reporting.ap.validation"),
    jetbrainsLibrary("com.jetbrains.fus.reporting.configuration"),
    jetbrainsLibrary("com.jetbrains.fus.reporting.connection.client"),
    jetbrainsLibrary("com.jetbrains.fus.reporting.model"),
    jetbrainsLibrary("com.jetbrains.fus.reporting.serialization.kotlin"),
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
    jetbrainsLibrary("completion-ranking-sh"),
    jetbrainsLibrary("completion-ranking-sh-exp"),
    jetbrainsLibrary("completion-ranking-swift-exp"),
    jetbrainsLibrary("completion-ranking-typescript-exp"),
    jetbrainsLibrary("debugger-agent"),
    jetbrainsLibrary("debugger-memory-agent"),
    jetbrainsLibrary("download-pgp-verifier"),
    jetbrainsLibrary("expects-compiler-plugin"),
    jetbrainsLibrary("file-prediction-model"),
    jetbrainsLibrary("find-action-model"),
    jetbrainsLibrary("find-action-model-experimental"),
    jetbrainsLibrary("find-all-model-experimental"),
    jetbrainsLibrary("find-classes-model"),
    jetbrainsLibrary("find-classes-model-experimental"),
    jetbrainsLibrary("find-ec-model-experimental"),
    jetbrainsLibrary("find-file-model"),
    jetbrainsLibrary("find-file-model-experimental"),
    jetbrainsLibrary("git-learning-project"),
    jetbrainsLibrary("intellij.remoterobot.remote.fixtures"),
    jetbrainsLibrary("intellij.remoterobot.robot.server.core"),
    jetbrainsLibrary("jetbrains.compose.hot.reload.gradle.idea"),
    jetbrainsLibrary("jetbrains.intellij.deps.rwmutex.idea"),
    jetbrainsLibrary("jetbrains.kotlin.compose.compiler.plugin"),
    jetbrainsLibrary("jetbrains.kotlin.jps.plugin.classpath"),
    jetbrainsLibrary("jetbrains.ml.models.jetenry.inline.prompt.detection.model"),
    jetbrainsLibrary("jetbrains.ml.models.python.imports.ranking.model"),
    jetbrainsLibrary("jetbrains.mlapi.catboost.shadow.need.slf4j"),
    jetbrainsLibrary("jetbrains.mlapi.ml.api"),
    jetbrainsLibrary("jetbrains.mlapi.ml.tools"),
    jetbrainsLibrary("jshell-frontend"),
    jetbrainsLibrary("jvm-native-trusted-roots"),
    jetbrainsLibrary("kotlin-gradle-plugin-idea"),
    jetbrainsLibrary("kotlin-gradle-plugin-idea-proto"),
    jetbrainsLibrary("kotlin-script-runtime"),
    jetbrainsLibrary("kotlin-test"),
    jetbrainsLibrary("kotlin-tooling-core"),
    jetbrainsLibrary("kotlinc.allopen-compiler-plugin"),
    jetbrainsLibrary("kotlinc.analysis-api"),
    jetbrainsLibrary("kotlinc.analysis-api-fe10"),
    jetbrainsLibrary("kotlinc.analysis-api-impl-base"),
    jetbrainsLibrary("kotlinc.analysis-api-impl-base-tests"),
    jetbrainsLibrary("kotlinc.analysis-api-k2"),
    jetbrainsLibrary("kotlinc.analysis-api-k2-tests"),
    jetbrainsLibrary("kotlinc.analysis-api-platform-interface"),
    jetbrainsLibrary("kotlinc.assignment-compiler-plugin"),
    jetbrainsLibrary("kotlinc.compose-compiler-plugin"),
    jetbrainsLibrary("kotlinc.incremental-compilation-impl-tests"),
    jetbrainsLibrary("kotlinc.js-plain-objects-compiler-plugin"),
    jetbrainsLibrary("kotlinc.kotlin-build-common-tests"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-cli"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-common"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-fe10"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-fir"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-ir"),
    jetbrainsLibrary("kotlinc.kotlin-compiler-tests"),
    jetbrainsLibrary("kotlinc.kotlin-dataframe-compiler-plugin"),
    jetbrainsLibrary("kotlinc.kotlin-dist"),
    jetbrainsLibrary("kotlinc.kotlin-gradle-statistics"),
    // TODO: KTIJ-32993
    jetbrainsLibrary("kotlinc.kotlin-ide-dist"),
    jetbrainsLibrary("kotlinc.kotlin-jps-common"),
    jetbrainsLibrary("kotlinc.kotlin-jps-plugin-classpath"),
    jetbrainsLibrary("kotlinc.kotlin-objcexport-header-generator"),
    jetbrainsLibrary("kotlinc.kotlin-script-runtime"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-common"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-compiler-impl"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-dependencies"),
    jetbrainsLibrary("kotlinc.kotlin-scripting-jvm"),
    jetbrainsLibrary("kotlinc.kotlin-swift-export"),
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
    jetbrainsLibrary("noria-compiler-plugin"),
    jetbrainsLibrary("rhizomedb-compiler-plugin"),
    jetbrainsLibrary("rpc-compiler-plugin"),
    jetbrainsLibrary("tcServiceMessages"),
    jetbrainsLibrary("terminal-completion-db-with-extensions"),
    jetbrainsLibrary("terminal-completion-spec"),
    jetbrainsLibrary("tips-idea-ce"),
    jetbrainsLibrary("tips-pycharm-community"),
    jetbrainsLibrary("workspace-model-codegen"),
    jetbrainsLibrary("RMI Stubs").copy(name = "XSLT Debugger RMI Stubs"),
  )

  private fun ffmpegLibraryLicense(name: String): LibraryLicense =
    LibraryLicense(name, libraryName = name, url = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/tags/studio-2022.3.1-beta2/common/m2/repository/org/bytedeco")
      .lgpl21plus("https://android.googlesource.com/platform/prebuilts/tools/+/refs/tags/studio-2022.3.1-beta2/common/m2/repository/org/bytedeco/ffmpeg-LICENSE.md")
      .suppliedByOrganizations(Suppliers.GOOGLE)

  private fun androidDependency(name: String, libraryName: String? = name, version: String? = null): LibraryLicense =
    LibraryLicense(name, libraryName = libraryName, version = version, url = "https://source.android.com/")
      .apache("https://source.android.com/setup/start/licenses")
      .copyrightText("Copyright (C) The Android Open Source Project")
      .suppliedByOrganizations(Suppliers.GOOGLE)

  private fun netty(libraryName: String): LibraryLicense =
    LibraryLicense(libraryName, libraryName = libraryName, url = "https://netty.io")
      .apache("https://github.com/netty/netty/blob/4.1/LICENSE.txt")
      .suppliedByOrganizations("The Netty project")
}
