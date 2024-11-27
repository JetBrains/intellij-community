package org.jetbrains.bazel.jvm.kotlin

import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile

data class JarOwner(
  @JvmField val jar: Path,
  @JvmField val label: String? = null,
  @JvmField val aspect: String? = null,
) {
  companion object {
    // These attributes are used by JavaBuilder, Turbine, and ijar.
    // They must all be kept in sync.
    @JvmField
    val TARGET_LABEL = Attributes.Name("Target-Label")
    @JvmField
    val INJECTING_RULE_KIND = Attributes.Name("Injecting-Rule-Kind")

    fun readJarOwnerFromManifest(jarPath: Path): JarOwner {
      JarFile(jarPath.toFile()).use { jarFile ->
        val manifest = jarFile.manifest ?: return JarOwner(jarPath)
        val attributes = manifest.mainAttributes
        val label =
          attributes[TARGET_LABEL] as String?
            ?: return JarOwner(jarPath)
        val injectingRuleKind = attributes[INJECTING_RULE_KIND] as String?
        return JarOwner(jar = jarPath, label = label, aspect = injectingRuleKind)
      }
    }
  }
}
