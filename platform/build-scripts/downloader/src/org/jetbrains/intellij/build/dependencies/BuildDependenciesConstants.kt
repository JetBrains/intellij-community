// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BuildDependenciesConstants {
  /**
   * Redirects the whole build-dependencies download cache (downloaded archives, extracted
   * directories, and extraction flag files) away from `<communityRoot>/build/download`.
   * For environments where the checkout is read-only, e.g. UI tests running in a VM off a
   * read-only share of the host checkout. Takes precedence over the TeamCity persistent cache.
   */
  const val DOWNLOAD_CACHE_DIR_PROPERTY: String = "intellij.build.download.cache.dir"

  /**
   * Points to the versioned manifests of Bazel runfiles which supply build-dependency downloads.
   * The value is one or more comma-separated entries - one per Bazel repository holding a part of the
   * set - each an absolute path or a Bazel rlocation path.
   *
   * A manifest supplies what it declares; a URL it does not declare is downloaded as usual, because a
   * dev-mode launch of any product reaches for archives no shared set can enumerate (a Go debugger, a
   * .NET SDK, notebook front-end resources). Set [PRELOADED_DOWNLOADS_ONLY_PROPERTY] where that
   * fallback must not happen.
   */
  const val PRELOADED_DOWNLOADS_MANIFEST_PROPERTY: String = "intellij.build.download.preloaded.manifest"

  /**
   * Makes the manifests named by [PRELOADED_DOWNLOADS_MANIFEST_PROPERTY] the complete inventory: an
   * undeclared URL is then an error, raised before any network or cache lookup, rather than a download.
   *
   * For runs that must not reach the network at all - a sandboxed test, a VM worker on a read-only
   * share of the checkout - where a silent download is a failure that would otherwise surface much
   * later, as a hang or as a stale cache entry standing in for the declared artifact.
   */
  const val PRELOADED_DOWNLOADS_ONLY_PROPERTY: String = "intellij.build.download.preloaded.only"

  const val INTELLIJ_DEPENDENCIES_URL: String = "https://cache-redirector.jetbrains.com/intellij-dependencies"
  const val MAVEN_CENTRAL_URL: String = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
  const val JPS_AUTH_SPACE_USERNAME: String = "jps.auth.spaceUsername"
  const val JPS_AUTH_SPACE_PASSWORD: String = "jps.auth.spacePassword"
}
