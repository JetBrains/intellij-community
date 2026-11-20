// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.nio.file.Path

internal class ManuallyWrittenAttributesTest {
  @Test
  fun `keeps an attribute only in a target of a rule that has it as a manually written attribute`() {
    val parsed = parseBuildFile(Path.of("BUILD.bazel"), """
      jvm_library(
          name = "module",
          srcs = [],
          # manually written attribute
          data = ["data.txt"],
          directory_name = "not-kept",
      )

      ij_plugin_module(
          name = "module2",
          # manually written attribute
          non_classpath_data = {
              "file.txt": "bin/file.txt",
          },
          data = ["data2.txt"],
          packed_deps_entry_renaming = {
              "//libs:library": {
                  "META-INF/LICENSE": "META-INF/LICENSE-library",
              },
          },
      )

      ij_plugin(
          name = "plugin_plugin",
          # manually written attribute
          directory_name = "custom-directory",
          non_classpath_data = "not-kept",
          packed_deps_entry_renaming = "not-kept",
      )

      jvm_test(
          name = "module_test",
          data = [":generated"],
      )

      native_binary(
          data = ["tool-data.txt"],
          name = "tool",
      )
    """.trimIndent())

    assertEquals(
      listOf(
        "jvm_library module data = [\"data.txt\"]",
        "ij_plugin_module module2 non_classpath_data = {\n        \"file.txt\": \"bin/file.txt\",\n    }",
        "ij_plugin_module module2 data = [\"data2.txt\"]",
        "ij_plugin_module module2 packed_deps_entry_renaming = {\n        \"//libs:library\": {\n            \"META-INF/LICENSE\": \"META-INF/LICENSE-library\",\n        },\n    }",
        "ij_plugin plugin_plugin directory_name = \"custom-directory\"",
      ).joinToString("\n"),
      parsed.attributes.joinToString("\n") { "${it.targetType} ${it.targetName} ${it.attributeName} = ${it.attributeValue}" },
    )
  }
}
