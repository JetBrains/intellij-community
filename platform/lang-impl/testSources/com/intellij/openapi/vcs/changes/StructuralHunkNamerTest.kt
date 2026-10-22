// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class StructuralHunkNamerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `a body change names the method and shows the changed line`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      """
      class Sample {
        int total() {
          return 1;
        }
      }
      """,
      """
      class Sample {
        int total() {
          return 2;
        }
      }
      """,
      HunkLines(2, 3, 2, 3),
    ).single()

    assertEquals(listOf("total(): int" to FileStatus.MODIFIED), name.parts.map { it.text to it.status })
    assertEquals("return 2", name.detail?.text)
    assertNotNull(name.icon)
    assertFalse(name.isWhitespaceOnly)
  }

  @Test
  fun `a presentation that shows the change needs no detail`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  int limit = 10;\n}",
      "class Sample {\n  int limit = 20;\n}",
      HunkLines(1, 2, 1, 2),
    ).single()

    assertEquals(listOf("limit: int = 20"), name.parts.map { it.text })
    assertNull(name.detail)
  }

  @Test
  fun `added and removed members hide their children`(): Unit = timeoutRunBlocking {
    val names = nameHunks(
      """
      class Sample {
        void removed() {
          first();
        }

        void stable() {
          second();
        }
      }
      """,
      """
      class Sample {
        void stable() {
          second();
        }

        class Added {
          void inner() {
            third();
          }
        }
      }
      """,
      HunkLines(1, 5, 1, 1),
      HunkLines(8, 8, 4, 10),
    )

    assertEquals(listOf(listOf("removed(): void" to FileStatus.DELETED), listOf("Added" to FileStatus.ADDED)),
                 names.map { name -> name.parts.map { it.text to it.status } })
    assertTrue(names.all { it.detail == null })
  }

  @Test
  fun `a rename pairs the most similar members and keeps a removed neighbor`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      """
      class Sample {
        void clear() {
          items.clear();
        }

        String describe() {
          return "items: " + items.size();
        }
      }
      """,
      """
      class Sample {
        String summary() {
          return "items: " + items.size();
        }
      }
      """,
      HunkLines(1, 6, 1, 2),
    ).single()

    assertEquals(listOf(Triple("summary(): String", FileStatus.MODIFIED, "describe(): String"),
                        Triple("clear(): void", FileStatus.DELETED, null)),
                 name.parts.map { Triple(it.text, it.status, it.previousText) })
  }

  @Test
  fun `different members are not a rename`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  int timeout = 30;\n}",
      "class Sample {\n  String disk = \"full\";\n}",
      HunkLines(1, 2, 1, 2),
    ).single()

    assertTrue(name.parts.none { it.previousText != null })
    assertEquals(setOf(FileStatus.ADDED, FileStatus.DELETED), name.parts.map { it.status }.toSet())
  }

  @Test
  fun `a line outside all elements names the hunk`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "import java.util.List;\n\nclass Sample {\n}",
      "import java.util.List;\nimport java.util.Map;\n\nclass Sample {\n}",
      HunkLines(1, 1, 1, 2),
    ).single()

    assertEquals(listOf("import java.util.Map" to FileStatus.ADDED), name.parts.map { it.text to it.status })
    assertNull(name.icon)
  }

  @Test
  fun `a comment between members names the hunk instead of the class`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  void first() {}\n\n  // Helpers.\n\n  void second() {}\n}",
      "class Sample {\n  void first() {}\n\n  // Helpers for the second method.\n\n  void second() {}\n}",
      HunkLines(3, 4, 3, 4),
    ).single()

    assertEquals(listOf("// Helpers for the second method."), name.parts.map { it.text })
  }

  @Test
  fun `a header change names the class`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  void first() {}\n}",
      "class Sample implements Runnable {\n  void first() {}\n}",
      HunkLines(0, 1, 0, 1),
    ).single()

    assertEquals(listOf("Sample" to FileStatus.MODIFIED), name.parts.map { it.text to it.status })
    assertEquals("class Sample implements Runnable {", name.detail?.text)
  }

  @Test
  fun `an indentation change is whitespace only`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  int total() {\n    return 1;\n  }\n}",
      "class Sample {\n  int total() {\n      return 1;\n  }\n}",
      HunkLines(2, 3, 2, 3),
    ).single()

    assertEquals(listOf("total(): int"), name.parts.map { it.text })
    assertTrue(name.isWhitespaceOnly)
    assertNull(name.detail)
  }

  @Test
  fun `blank lines have no parts`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  void first() {}\n  void second() {}\n}",
      "class Sample {\n  void first() {}\n\n  void second() {}\n}",
      HunkLines(2, 2, 2, 3),
    ).single()

    assertTrue(name.parts.isEmpty())
  }

  @Test
  fun `a deletion inside a method names the method`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  int total() {\n    log();\n    return 1;\n  }\n}",
      "class Sample {\n  int total() {\n    return 1;\n  }\n}",
      HunkLines(2, 3, 2, 2),
    ).single()

    assertEquals(listOf("total(): int" to FileStatus.MODIFIED), name.parts.map { it.text to it.status })
    assertEquals("log()" to FileStatus.DELETED, name.detail?.let { it.text to it.status })
  }

  @Test
  fun `a file without a baseline names every hunk as added`(): Unit = timeoutRunBlocking {
    val after = "class Sample {\n  void first() {}\n}"
    val name = smartReadAction(projectFixture.get()) {
      val file = PsiFileFactory.getInstance(projectFixture.get()).createFileFromText("Sample.java", javaFileType(), after)
      StructuralHunkNamer.name(null, file, listOf(HunkLines(0, 0, 0, 3))).single()
    }

    assertEquals(listOf("Sample" to FileStatus.ADDED), name.parts.map { it.text to it.status })
  }

  /** Names the hunks of two versions of `Sample.java`. The versions lose their common indent, as in [String.trimIndent]. */
  private suspend fun nameHunks(before: String, after: String, vararg hunks: HunkLines): List<HunkName> {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    val beforeText = before.trimIndent()
    val afterText = after.trimIndent()
    return smartReadAction(project) {
      val factory = PsiFileFactory.getInstance(project)
      val beforeFile = factory.createFileFromText("Sample.java", javaFileType(), beforeText)
      val afterFile = factory.createFileFromText("Sample.java", javaFileType(), afterText)
      StructuralHunkNamer.name(beforeFile, afterFile, hunks.asList())
    }
  }

  private fun javaFileType() = FileTypeManager.getInstance().getFileTypeByFileName("Sample.java")
}
