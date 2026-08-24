// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"regexp"
	"strings"
)

// IndexFileName is the generated per-jar class-path index. It is dropped on the way in and rewritten for the output,
// so a jar packed from jars that carry one does not inherit a stale index.
const IndexFileName = "__index__"

// ManifestEntryName is the one entry whose survival is decided per jar rather than by its name - see Merge.
const ManifestEntryName = "META-INF/MANIFEST.MF"

// ModuleOutputNameFilter reports whether an entry of a *module output* jar belongs in a distribution jar.
//
// Far narrower than LibraryNameFilter, and for the reason the Kotlin original gives: a module output is ours, so the
// only things to drop are the icon-rule file that is a build-time input and the leftovers the compilation cache writes
// into an output directory. Ported from `defaultModuleOutputNamesFilter` in zip/src/jarMerger.kt.
func ModuleOutputNameFilter(name string) bool {
	return name != "icon-robots.txt" &&
		!strings.HasSuffix(name, "/icon-robots.txt") &&
		name != ".unmodified" &&
		name != ".hash" &&
		name != "classpath.index" &&
		name != "module-info.class"
}

// moduleInfoPattern is anchored, matching Kotlin's `Regex.matches`, which requires the whole name.
var moduleInfoPattern = regexp.MustCompile(`^META-INF/versions/\d+/module-info\.class$`)

// ignoredLibraryNames is the exact-name set from `getIgnoredNames` in zip/src/librarySourcesFilter.kt, in the order
// that file builds it. Kept as a literal rather than rebuilt from parts so it can be diffed against the original.
var ignoredLibraryNames = func() map[string]struct{} {
	names := []string{
		// compilation cache on TC
		".hash", "classpath.index", ".gitattributes", "pom.xml", "about.html", "module-info.class",

		// default is ok (modules not used)
		"META-INF/versions/9/kotlin/reflect/jvm/internal/impl/serialization/deserialization/builtins/BuiltInsResourceLoader.class",
		"META-INF/versions/9/org/apache/xmlbeans/impl/tool/MavenPluginResolver.class",
		"META-INF/services/javax.xml.parsers.SAXParserFactory",
		"META-INF/services/javax.xml.stream.XMLEventFactory",
		"META-INF/services/javax.xml.parsers.DocumentBuilderFactory",
		"META-INF/services/javax.xml.datatype.DatatypeFactory",

		"META-INF/services/com.fasterxml.jackson.core.ObjectCodec",
		"META-INF/services/com.fasterxml.jackson.core.JsonFactory",
		"META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",

		"META-INF/io.netty.versions.properties",

		"com/sun/jna/aix-ppc/libjnidispatch.a",
		"com/sun/jna/aix-ppc64/libjnidispatch.a",

		// duplicates in maven-resolver-transport-http and maven-resolver-transport-file
		"META-INF/sisu/javax.inject.Named",

		// duplicates in recommenders-jayes-io-2.5.5 and recommenders-jayes-2.5.5.jar
		"OSGI-INF/l10n/bundle.properties",

		// Groovy
		"META-INF/groovy-release-info.properties",

		"native-image", "native", "licenses", "META-INF/LGPL2.1", "META-INF/AL2.0", ".gitkeep", IndexFileName,

		"kotlinx/coroutines/debug/ByteBuddyDynamicAttach.class",
		"kotlin/coroutines/jvm/internal/DebugProbesKt.class",

		// A merging build politic breaks Graal VM Truffle-based plugins in an inconsistant way, so it's better to
		// provide a correctly merged version in the plugin.
		"META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider",
	}
	set := make(map[string]struct{}, len(names)+84)
	for _, name := range names {
		set[name] = struct{}{}
	}
	// The legal files, generated the same way the original generates them: each base name in its given and
	// lower-cased spelling, bare and under META-INF/, with no extension, `.txt` and `.md`.
	for _, original := range []string{"NOTICE", "README", "LICENSE", "DEPENDENCIES", "CHANGES", "THIRD_PARTY_LICENSES", "COPYING"} {
		for _, name := range []string{original, strings.ToLower(original)} {
			for _, n := range []string{name, name + ".txt", name + ".md", "META-INF/" + name, "META-INF/" + name + ".txt", "META-INF/" + name + ".md"} {
				set[n] = struct{}{}
			}
		}
	}
	return set
}()

// ignoredLibraryPrefixes is the prefix list from `defaultLibrarySourcesNamesFilter`, in source order.
var ignoredLibraryPrefixes = []string{
	"license/",
	"licenses/",
	"native/",
	"META-INF/license/",
	"META-INF/LICENSE-",
	"native-image/",
	"org/xml/sax/", // XmlRPC lib
	"META-INF/versions/9/org/apache/logging/log4j/",
	"META-INF/versions/9/org/bouncycastle/",
	"META-INF/versions/10/org/bouncycastle/",
	"META-INF/versions/15/org/bouncycastle/",
	"kotlinx/coroutines/repackaged/",
	"META-INF/INDEX.LIST",
	"net/sf/cglib/core/AbstractClassGenerator", // we replace the lib class with our own patched version
}

// LibraryNameFilter reports whether an entry of a third-party *library* jar belongs in a distribution jar.
//
// Ported from `defaultLibrarySourcesNamesFilter`. The Kotlin original's own comment explains why it is shared between
// the two producers: it is a pure function of the entry name, and a disagreement would show up as a jar that differs
// depending on which packer produced it. That argument now spans two languages, which is exactly why the parity check
// over every packed jar is not optional.
func LibraryNameFilter(name string) bool {
	if _, ignored := ignoredLibraryNames[name]; ignored {
		return false
	}
	if moduleInfoPattern.MatchString(name) {
		return false
	}
	if strings.HasSuffix(name, ".kotlin_metadata") {
		return false
	}
	for _, prefix := range ignoredLibraryPrefixes {
		if strings.HasPrefix(name, prefix) {
			return false
		}
	}
	if strings.HasPrefix(name, "META-INF/") &&
		(strings.HasSuffix(name, ".DSA") || strings.HasSuffix(name, ".SF") || strings.HasSuffix(name, ".RSA")) {
		return false
	}
	return true
}
