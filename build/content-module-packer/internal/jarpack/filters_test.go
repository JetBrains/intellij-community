// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import "testing"

// These two filters are shared with the Kotlin `JarPackager` by having been ported, not by being called - so a
// disagreement shows up as a jar that differs depending on which packer produced it. The names below are the ones whose
// classification is decided by a pattern rather than by a literal, which is where a port drifts.

func TestModuleOutputNameFilter(t *testing.T) {
	for name, want := range map[string]bool{
		"com/example/Service.class":     true,
		"messages/Bundle.properties":    true,
		"icon-robots.txt":               false,
		"com/example/icon-robots.txt":   false,
		"my-icon-robots.txt":            true,
		".unmodified":                   false,
		".hash":                         false,
		"classpath.index":               false,
		"module-info.class":             false,
		"com/example/module-info.class": true,
	} {
		if got := ModuleOutputNameFilter(name); got != want {
			t.Errorf("ModuleOutputNameFilter(%q) is %v, want %v", name, got, want)
		}
	}
}

func TestLibraryNameFilter(t *testing.T) {
	for name, want := range map[string]bool{
		"org/thirdparty/Api.class":                    true,
		"META-INF/services/org.thirdparty.Spi":        true,
		"META-INF/versions/11/org/thirdparty/A.class": true,
		"META-INF/versions/9/module-info.class":       false,
		"META-INF/versions/17/module-info.class":      false,
		"META-INF/versions/module-info.class":         true,
		"META-INF/versions/9/x/module-info.class":     true,
		"module-info.class":                           false,
		"org/thirdparty/Api.kotlin_metadata":          false,
		"LICENSE":                                     false,
		"license":                                     false,
		"META-INF/COPYING.md":                         false,
		"licenses/apache.txt":                         false,
		"native-image/config.json":                    false,
		"META-INF/LICENSE-apache":                     false,
		"META-INF/SIGNER.SF":                          false,
		"META-INF/SIGNER.RSA":                         false,
		"META-INF/SIGNER.DSA":                         false,
		"org/thirdparty/SIGNER.SF":                    true,
		"org/xml/sax/InputSource.class":               false,
		"kotlinx/coroutines/repackaged/A.class":       false,
		"META-INF/io.netty.versions.properties":       false,
		"pom.xml":                                     false,
	} {
		if got := LibraryNameFilter(name); got != want {
			t.Errorf("LibraryNameFilter(%q) is %v, want %v", name, got, want)
		}
	}
}
