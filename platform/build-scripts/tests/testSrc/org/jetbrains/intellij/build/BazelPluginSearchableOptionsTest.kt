// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.searchableOptionsInjector.injectSearchableOptions
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.plugins.computeSearchableOptionsInjections
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val MAIN_MODULE = "intellij.my.plugin"
private const val PLUGIN_ID = "my.plugin"
private const val CONTENT_MODULE = "intellij.my.plugin.optional"

/**
 * Tests injection of the searchable options index into plugin distributions built by the `ij_plugin` Bazel rule.
 */
class BazelPluginSearchableOptionsTest {
  @Test
  fun `options are added to jars of the modules they belong to`(@TempDir tempDirectory: Path) {
    val pluginDir = tempDirectory.resolve("my-plugin")
    directoryContent {
      dir("lib") {
        zip("descriptor.jar") {
          dir("META-INF") {
            file("plugin.xml", "<idea-plugin><id>$PLUGIN_ID</id></idea-plugin>")
          }
        }
        dir("modules") {
          zip("$CONTENT_MODULE.jar") {
            file("$CONTENT_MODULE.xml", "<idea-plugin></idea-plugin>")
          }
          zip("intellij.my.plugin.without.options.jar") {
            file("intellij.my.plugin.without.options.xml", "<idea-plugin></idea-plugin>")
          }
        }
      }
    }.generate(pluginDir)

    val searchableOptionSet = createSearchableOptionSet(tempDirectory.resolve("searchable-options"))
    injectSearchableOptions(computeSearchableOptionsInjections(
      distributionFileEntries = listOf(
        moduleEntry(pluginDir.resolve("lib/descriptor.jar"), MAIN_MODULE, isContentModule = false),
        moduleEntry(pluginDir.resolve("lib/modules/$CONTENT_MODULE.jar"), CONTENT_MODULE, isContentModule = true),
        moduleEntry(pluginDir.resolve("lib/modules/intellij.my.plugin.without.options.jar"), "intellij.my.plugin.without.options", isContentModule = true),
      ),
      mainModule = MAIN_MODULE,
      pluginId = PLUGIN_ID,
      searchableOptionSet = searchableOptionSet,
    ))

    pluginDir.assertMatches(directoryContent {
      dir("lib") {
        zip("descriptor.jar") {
          file("__index__")
          file("p-$PLUGIN_ID-searchableOptions.json", "plugin options")
          dir("META-INF") {
            file("plugin.xml", "<idea-plugin><id>$PLUGIN_ID</id></idea-plugin>")
          }
        }
        dir("modules") {
          zip("$CONTENT_MODULE.jar") {
            file("__index__")
            file("m-$CONTENT_MODULE-searchableOptions.json", "module options")
            file("$CONTENT_MODULE.xml", "<idea-plugin></idea-plugin>")
          }
          // a module without searchable options is not rewritten at all
          zip("intellij.my.plugin.without.options.jar") {
            file("intellij.my.plugin.without.options.xml", "<idea-plugin></idea-plugin>")
          }
        }
      }
    })
  }

  @Test
  fun `main module options are looked up by plugin id and content module options by module name`(@TempDir tempDirectory: Path) {
    val searchableOptionSet = createSearchableOptionSet(tempDirectory.resolve("searchable-options"))
    val injections = computeSearchableOptionsInjections(
      distributionFileEntries = listOf(
        // the index also has an entry under the main module name, which must not be picked up: the main module's options are stored under the plugin ID
        moduleEntry(tempDirectory.resolve("lib/descriptor.jar"), MAIN_MODULE, isContentModule = false),
        moduleEntry(tempDirectory.resolve("lib/modules/$CONTENT_MODULE.jar"), CONTENT_MODULE, isContentModule = true),
      ),
      mainModule = MAIN_MODULE,
      pluginId = PLUGIN_ID,
      searchableOptionSet = searchableOptionSet,
    )

    assertEquals(
      listOf(
        tempDirectory.resolve("lib/descriptor.jar") to listOf("p-$PLUGIN_ID-searchableOptions.json"),
        tempDirectory.resolve("lib/modules/$CONTENT_MODULE.jar") to listOf("m-$CONTENT_MODULE-searchableOptions.json"),
      ),
      injections.map { injection -> injection.jar to injection.entries.map { it.entryName } },
    )
  }

  private fun createSearchableOptionSet(baseDir: Path): SearchableOptionSetDescriptor {
    baseDir.createDirectories()
    val pluginOptions = "p-$PLUGIN_ID-searchableOptions.json"
    val moduleOptions = "m-$CONTENT_MODULE-searchableOptions.json"
    baseDir.resolve(pluginOptions).writeText("plugin options")
    baseDir.resolve(moduleOptions).writeText("module options")
    val decoyOptions = "m-$MAIN_MODULE-searchableOptions.json"
    baseDir.resolve(decoyOptions).writeText("decoy options")
    return SearchableOptionSetDescriptor(
      index = mapOf(
        PLUGIN_ID to listOf(SearchableOptionSetIndexItem(file = pluginOptions, size = 14, hash = 1)),
        CONTENT_MODULE to listOf(SearchableOptionSetIndexItem(file = moduleOptions, size = 14, hash = 2)),
        MAIN_MODULE to listOf(SearchableOptionSetIndexItem(file = decoyOptions, size = 12, hash = 3)),
      ),
      baseDir = baseDir,
    )
  }

  private fun moduleEntry(jar: Path, moduleName: String, isContentModule: Boolean): ModuleOutputEntry {
    val reason = if (isContentModule) generateInclusionReasonForContentModule(MAIN_MODULE) else null
    val moduleItem = ModuleItem(moduleName = moduleName, relativeOutputFile = jar.fileName.toString(), reason = reason)
    return ModuleOutputEntry(path = jar, owner = moduleItem, size = 0, hash = 0, relativeOutputFile = moduleItem.relativeOutputFile, reason = reason)
  }
}
