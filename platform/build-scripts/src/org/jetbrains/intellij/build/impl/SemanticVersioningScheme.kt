// This source code is based on the Semantic Versioning specification (https://semver.org/) originally authored by Tom Preston-Werner (https://tom.preston-werner.com/) and copyrighted under the Creative Commons Attribution 3.0 license (https://creativecommons.org/licenses/by/3.0/).
package org.jetbrains.intellij.build.impl

internal object SemanticVersioningScheme {
  private val SEM_VER_REGEX = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$")

  fun matches(version: String): Boolean {
    return SEM_VER_REGEX.matches(version)
  }
}