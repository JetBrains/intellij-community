// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProjectLibraryEntry
import com.intellij.platform.distributionContent.ProjectLibraryFile
import com.intellij.platform.distributionContent.deserializeContentData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

  @Test
  fun `do not require review if only dependent modules were changed`(@TempDir dir: Path) {
    val expected = listOf(
      FileEntry(
        name = "foo.jar",
        projectLibraries = listOf(
          ProjectLibraryEntry(
            name = "fooLib",
            files = listOf(
              ProjectLibraryFile("fooLib.jar")
            ),
            dependentModules = linkedMapOf("foo" to listOf("a", "b"))
          )
        )
      )
    )
    val actual = listOf(
      FileEntry(
        name = "foo.jar",
        projectLibraries = listOf(
          ProjectLibraryEntry(
            name = "fooLib",
            files = listOf(
              ProjectLibraryFile("fooLib.jar")
            ),
            dependentModules = linkedMapOf("foo" to listOf("a", "b", "new"))
          )
        )
      )
    )

    val serializer = ListSerializer(FileEntry.serializer())
    val expectedFile = dir.resolve("expected.json")
    val actualFile = dir.resolve("actual.json")
    Files.writeString(expectedFile, Json.encodeToString(serializer, expected))
    Files.writeString(actualFile, Json.encodeToString(serializer, actual))

    val message = assertThatThrownBy {
      checkThatContentIsNotChanged(
        actualFileEntries = deserializeContentData(Files.readString(actualFile)),
        expectedFile = expectedFile,
        projectHome = dir,
        isBundled = true,
        suggestedReviewer = "reviewer-12345"
      )
    }.message()
    message.contains("commit a new snapshot")
    message.doesNotContain("reviewer-12345")
  }

  @Test
  fun `require review if not only dependent modules were changed`(@TempDir dir: Path) {
    val expected = listOf(
      FileEntry(
        name = "foo.jar",
        projectLibraries = listOf(
          ProjectLibraryEntry(
            name = "fooLib",
            files = listOf(
              ProjectLibraryFile("fooLib.jar")
            ),
            dependentModules = linkedMapOf("foo" to listOf("a", "b"))
          )
        )
      )
    )
    val actual = listOf(
      FileEntry(
        name = "foo.jar",
        projectLibraries = listOf(
          ProjectLibraryEntry(
            name = "fooLib",
            files = listOf(
              ProjectLibraryFile("fooLib.jar")
            ),
            dependentModules = linkedMapOf("foo" to listOf("a", "b"))
          ),
          ProjectLibraryEntry(
            name = "newLib",
            files = listOf(
              ProjectLibraryFile("newLib.jar")
            ),
            dependentModules = linkedMapOf("foo" to listOf("c"))
          )
        )
      )
    )

    val serializer = ListSerializer(FileEntry.serializer())
    val expectedFile = dir.resolve("expected.json")
    val actualFile = dir.resolve("actual.json")
    Files.writeString(expectedFile, Json.encodeToString(serializer, expected))
    Files.writeString(actualFile, Json.encodeToString(serializer, actual))

    val actualReviewRequiredMessage = assertThatThrownBy {
      checkThatContentIsNotChanged(
        actualFileEntries = deserializeContentData(Files.readString(actualFile)),
        expectedFile = expectedFile,
        projectHome = dir,
        isBundled = true,
        suggestedReviewer = "reviewer-12345",
      )
    }.message()
    actualReviewRequiredMessage.contains("reviewer-12345")

    // review is not required when suggestedReviewer = null
    val actualReviewNotRequiredMessage = assertThatThrownBy {
      checkThatContentIsNotChanged(
        actualFileEntries = deserializeContentData(Files.readString(actualFile)),
        expectedFile = expectedFile,
        projectHome = dir,
        isBundled = true,
        suggestedReviewer = null
      )
    }.message()
    actualReviewNotRequiredMessage.contains("commit a new snapshot")
    actualReviewNotRequiredMessage.doesNotContain("reviewer-12345")
  }
}
