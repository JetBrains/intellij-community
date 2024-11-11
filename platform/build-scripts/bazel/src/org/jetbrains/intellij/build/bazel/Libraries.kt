// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path

internal sealed class Library(
  @JvmField val targetName: String,
  @JvmField val isProvided: Boolean,
  // excluded from equals / hashCode
  @JvmField val isCommunity: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Library

    if (isProvided != other.isProvided) return false
    if (targetName != other.targetName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = isProvided.hashCode()
    result = 31 * result + targetName.hashCode()
    return result
  }
}

internal class MavenLibrary(
  @JvmField val mavenCoordinates: String,
  @JvmField val jars: List<Path>,
  @JvmField val sourceJars: List<Path>,
  @JvmField val javadocJars: List<Path>,
  targetName: String,
  isProvided: Boolean,
  isCommunity: Boolean,
): Library(targetName = targetName, isProvided = isProvided, isCommunity = isCommunity) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as MavenLibrary

    if (mavenCoordinates != other.mavenCoordinates) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + mavenCoordinates.hashCode()
    return result
  }
}

internal class LocalLibrary(
  @JvmField val files: List<Path>,
  // excluded from equals and hashCode
  @JvmField val bazelLabel: String,
  targetName: String,
  isProvided: Boolean,
  isCommunity: Boolean,
): Library(targetName = targetName, isProvided = isProvided, isCommunity = isCommunity) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as LocalLibrary

    if (files != other.files) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + files.hashCode()
    return result
  }
}