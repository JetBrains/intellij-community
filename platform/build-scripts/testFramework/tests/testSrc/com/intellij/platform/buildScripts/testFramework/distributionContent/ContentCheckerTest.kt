// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProjectLibraryEntry
import com.intellij.platform.distributionContent.ProjectLibraryFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentCheckerTest {
  @Test
  fun `a single target platform is returned as it is`() {
    val content = listOf(FileEntry(name = "lib/x.jar", modules = listOf(ModuleEntry(name = "m", size = 17))))
    val merged = mergePerOsPluginContent(listOf(PluginContentReport(mainModule = "p", content = content)))
    assertThat(merged).isEqualTo(content)
  }

  @Test
  fun `target platforms that differ only in dependent modules union to one entry`() {
    fun variant(os: String, dependentModules: List<String>) = PluginContentReport(
      mainModule = "p",
      os = os,
      content = listOf(
        FileEntry(
          name = "lib/x.jar",
          projectLibraries = listOf(
            ProjectLibraryEntry(
              name = "fooLib",
              files = listOf(ProjectLibraryFile("fooLib.jar")),
              dependentModules = linkedMapOf("m" to dependentModules),
            )
          ),
        )
      ),
    )

    // `dependentModules` is the field `short = true` erases, so the comparison has already declined to look at it. A
    // union keyed on the unerased values kept both variants and made the comparison see `lib/x.jar` twice.
    val merged = mergePerOsPluginContent(listOf(variant("mac", listOf("a")), variant("windows", listOf("a", "b"))))
    assertThat(merged.map { it.name }).containsExactly("lib/x.jar")
  }
}
