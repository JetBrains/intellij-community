// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

internal class LibraryUpstream(
  val mavenRepositoryUrl: String?,
  val sourceCodeUrl: String?,
  val groupId: String,
  val artifactId: String,
  val version: String?,
  val revision: String?,
  val license: LibraryLicense
) {
  init {
    check(version == null || mavenRepositoryUrl != null) {
      "Missing Maven repository url for $groupId:$artifactId:$version"
    }
    check(mavenRepositoryUrl == null || version != null) {
      "Missing version for $groupId:$artifactId:$version"
    }
    check(revision == null || version == null) {
      "Version is already specified for $groupId:$artifactId:$version, revision is not required"
    }
    check(revision == null || sourceCodeUrl != null) {
      "Revision is specified but source code url is missing for $groupId:$artifactId:$version"
    }
  }
}