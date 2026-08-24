// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

/**
 * Which entries of a *library* jar are packed into a distribution jar.
 *
 * It lives here, beside the writer, because both producers of distribution jars need the same answer: the in-process
 * `JarPackager` and the Bazel content-module packer. It is a pure function of the entry name - no project model, no
 * layout - so sharing it costs nothing and keeps the two from drifting apart, which would show up as a jar that
 * differs depending on which packer produced it.
 *
 * Module *outputs* are filtered more narrowly (`icon-robots.txt` and the build-cache leftovers); this set is for the
 * third-party jars merged in beside them, where duplicate manifests, licences, signatures and multi-release
 * `module-info.class` entries would otherwise collide or ship for nothing.
 */
@Suppress("SpellCheckingInspection")
private fun getIgnoredNames(): Set<String> {
  val set = mutableListOf<String>()

  // compilation cache on TC
  set.add(".hash")
  set.add("classpath.index")
  set.add(".gitattributes")
  set.add("pom.xml")
  set.add("about.html")
  set.add("module-info.class")

  // default is ok (modules not used)
  set.add("META-INF/versions/9/kotlin/reflect/jvm/internal/impl/serialization/deserialization/builtins/BuiltInsResourceLoader.class")
  set.add("META-INF/versions/9/org/apache/xmlbeans/impl/tool/MavenPluginResolver.class")
  set.add("META-INF/services/javax.xml.parsers.SAXParserFactory")
  set.add("META-INF/services/javax.xml.stream.XMLEventFactory")
  set.add("META-INF/services/javax.xml.parsers.DocumentBuilderFactory")
  set.add("META-INF/services/javax.xml.datatype.DatatypeFactory")

  set.add("META-INF/services/com.fasterxml.jackson.core.ObjectCodec")
  set.add("META-INF/services/com.fasterxml.jackson.core.JsonFactory")
  set.add("META-INF/services/reactor.blockhound.integration.BlockHoundIntegration")

  set.add("META-INF/io.netty.versions.properties")

  set.add("com/sun/jna/aix-ppc/libjnidispatch.a")
  set.add("com/sun/jna/aix-ppc64/libjnidispatch.a")

  // duplicates in maven-resolver-transport-http and maven-resolver-transport-file
  set.add("META-INF/sisu/javax.inject.Named")

  // duplicates in recommenders-jayes-io-2.5.5 and recommenders-jayes-2.5.5.jar
  set.add("OSGI-INF/l10n/bundle.properties")

  // Groovy
  set.add("META-INF/groovy-release-info.properties")

  set.add("native-image")
  set.add("native")
  set.add("licenses")
  set.add("META-INF/LGPL2.1")
  set.add("META-INF/AL2.0")
  set.add(".gitkeep")
  set.add(INDEX_FILENAME)

  for (originalName in sequenceOf("NOTICE", "README", "LICENSE", "DEPENDENCIES", "CHANGES", "THIRD_PARTY_LICENSES", "COPYING")) {
    for (name in sequenceOf(originalName, originalName.lowercase())) {
      set.add(name)
      set.add("$name.txt")
      set.add("$name.md")
      set.add("META-INF/$name")
      set.add("META-INF/$name.txt")
      set.add("META-INF/$name.md")
    }
  }

  set.add("kotlinx/coroutines/debug/ByteBuddyDynamicAttach.class")
  set.add("kotlin/coroutines/jvm/internal/DebugProbesKt.class")

  /**
   * A merging build politic breaks Graal VM Truffle-based plugins in an inconsistant way,
   * so it's better to provide a correctly merged version in the plugin.
   */
  set.add("META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider")

  return java.util.Set.copyOf(set)
}

private val ignoredNames = getIgnoredNames()
private val moduleInfoPattern = Regex("META-INF/versions/\\d+/module-info\\.class")

fun defaultLibrarySourcesNamesFilter(name: String): Boolean {
  return !ignoredNames.contains(name) &&
         !name.matches(moduleInfoPattern) &&
         !name.endsWith(".kotlin_metadata") &&
         !name.startsWith("license/") &&
         !name.startsWith("licenses/") &&
         !name.startsWith("native/") &&
         !name.startsWith("META-INF/license/") &&
         !name.startsWith("META-INF/LICENSE-") &&
         !name.startsWith("native-image/") &&
         !name.startsWith("org/xml/sax/") &&  // XmlRPC lib
         !name.startsWith("META-INF/versions/9/org/apache/logging/log4j/") &&
         !name.startsWith("META-INF/versions/9/org/bouncycastle/") &&
         !name.startsWith("META-INF/versions/10/org/bouncycastle/") &&
         !name.startsWith("META-INF/versions/15/org/bouncycastle/") &&
         !name.startsWith("kotlinx/coroutines/repackaged/") &&
         !name.startsWith("META-INF/INDEX.LIST") &&
         (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA"))) &&
         !name.startsWith("net/sf/cglib/core/AbstractClassGenerator")  // we replace the lib class with our own patched version
}
