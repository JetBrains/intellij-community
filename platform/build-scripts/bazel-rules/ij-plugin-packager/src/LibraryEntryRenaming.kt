package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.Path

/**
 * The rules from the `packed_deps_entry_renaming` attribute of `ij_plugin_module` for the library JARs of one module.
 * A rule renames an entry of a group of JARs, or skips it.
 */
internal class LibraryEntryRenaming(private val moduleName: String) {
  private val rules = ArrayList<Rule>()
  private val rulesByJar = HashMap<Path, HashMap<String, Rule>>()

  /** Adds a rule that gives the entry [sourcePath] of [jars] the path [targetPath], or skips it if [targetPath] is `null`. */
  fun addRule(sourcePath: String, targetPath: String?, jars: List<Path>) {
    val rule = Rule(sourcePath, targetPath, jars)
    rules.add(rule)
    for (jar in jars) {
      if (rulesByJar.getOrPut(jar) { HashMap() }.put(sourcePath, rule) != null) {
        throw IjPluginPackagingException("Two rules in 'packed_deps_entry_renaming' of '$moduleName' rename or skip '$sourcePath' in $jar")
      }
    }
  }

  /** Returns the path of the entry [sourcePath] of [jar] in the output JAR, or `null` if the entry is skipped. */
  fun entryPath(jar: Path, sourcePath: String): String? {
    val rule = rulesByJar.get(jar)?.get(sourcePath) ?: return sourcePath
    rule.applied = true
    return rule.targetPath
  }

  /** Reports an error if a rule names an entry that none of its JARs contains. */
  fun checkAllRulesApplied() {
    val notApplied = rules.filter { !it.applied }
    if (notApplied.isNotEmpty()) {
      throw IjPluginPackagingException(
        "'packed_deps_entry_renaming' of '$moduleName' specifies entries that are not found in the JARs: " +
        notApplied.joinToString { "'${it.sourcePath}' in ${it.jars.joinToString()}" }
      )
    }
  }

  private class Rule(
    val sourcePath: String,
    val targetPath: String?,
    val jars: List<Path>,
  ) {
    var applied: Boolean = false
  }
}
