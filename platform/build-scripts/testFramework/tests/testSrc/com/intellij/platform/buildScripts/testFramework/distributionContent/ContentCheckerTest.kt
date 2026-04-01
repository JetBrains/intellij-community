// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.ProjectLibraryEntry
import com.intellij.platform.distributionContent.testFramework.ProjectLibraryFile
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ContentCheckerTest {
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
