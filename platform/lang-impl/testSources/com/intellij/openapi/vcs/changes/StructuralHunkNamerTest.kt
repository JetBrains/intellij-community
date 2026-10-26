// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

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
  fun `a group above the rename pair limit shows added and removed members`(): Unit = timeoutRunBlocking {
    val renamed = nameHunks(renamedMethods(100, "old"), renamedMethods(100, "new"), HunkLines(1, 101, 1, 101)).single()
    assertEquals(100, renamed.parts.size)
    assertEquals(Triple("new0(): void", FileStatus.MODIFIED, "old0(): void"),
                 renamed.parts.first().let { Triple(it.text, it.status, it.previousText) })

    val large = nameHunks(renamedMethods(1000, "old"), renamedMethods(1000, "new"), HunkLines(1, 1001, 1, 1001)).single()
    assertEquals(2000, large.parts.size)
    assertTrue(large.parts.none { it.previousText != null })
    assertEquals(listOf("new0(): void" to FileStatus.ADDED, "old0(): void" to FileStatus.DELETED),
                 large.parts.take(2).map { it.text to it.status })
  }

  @Test
  fun `ten thousand renamed methods get a name in time`(): Unit = timeoutRunBlocking(timeout = 1.minutes) {
    val name = nameHunks(renamedMethods(10_000, "old"), renamedMethods(10_000, "new"), HunkLines(1, 10_001, 1, 10_001)).single()

    assertEquals(20_000, name.parts.size)
    assertTrue(name.parts.none { it.previousText != null })
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
  fun `an initializer inserted before equal initializers is added`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  static { a(); }\n  static { b(); }\n}",
      "class Sample {\n  static { z(); }\n  static { a(); }\n  static { b(); }\n}",
      HunkLines(1, 1, 1, 2),
    ).single()

    assertEquals(listOf(FileStatus.ADDED), name.parts.map { it.status })
  }

  @Test
  fun `a changed initializer among equal initializers is modified`(): Unit = timeoutRunBlocking {
    val name = nameHunks(
      "class Sample {\n  static { a(); }\n  static { b(); }\n}",
      "class Sample {\n  static { c(); }\n  static { b(); }\n}",
      HunkLines(1, 2, 1, 2),
    ).single()

    assertEquals(listOf(FileStatus.MODIFIED), name.parts.map { it.status })
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

  @Test
  fun `snapshots keep the file context without changing its content`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    smartReadAction(project) {
      val originalText = "class Sample {\n  int value() { return 1; }\n}"
      val changedText = "class Sample {\n  int value() { return 2; }\n}"
      val original = PsiFileFactory.getInstance(project).createFileFromText("Sample.java", javaFileType(), originalText, 0, true)
      val before = requireNotNull(StructuralHunkNamer.createSnapshot(original, originalText))
      val after = requireNotNull(StructuralHunkNamer.createSnapshot(original, changedText))

      assertSame(original, before.originalFile)
      assertSame(original, after.originalFile)
      assertNotSame(original.virtualFile, before.virtualFile)
      assertNotSame(before.virtualFile, after.virtualFile)
      assertEquals(originalText, original.text)
      assertEquals(changedText, after.text)
      val name = StructuralHunkNamer.name(before, after, listOf(HunkLines(1, 2, 1, 2))).single()
      assertEquals(listOf("int value() { return 2; }" to FileStatus.MODIFIED), name.parts.map { it.text to it.status })
    }
  }

  @Test
  fun `a secondary PSI root names the hunks of its file`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    val javaLanguage = requireNotNull(Language.findLanguageByID("JAVA"))
    val builders = LanguageStructureViewBuilder.getInstance()
    builders.addExplicitExtension(PlainTextLanguage.INSTANCE, PsiStructureViewFactory { file ->
      builders.getStructureViewBuilder(requireNotNull(file.viewProvider.getPsi(javaLanguage)))
    }, disposable)
    smartReadAction(project) {
      val manager = PsiManager.getInstance(project)
      fun file(text: String): PsiFile {
        val provider = JavaTemplateViewProvider(manager, LightVirtualFile("Sample.txt", PlainTextFileType.INSTANCE, text), javaLanguage)
        return requireNotNull(provider.getPsi(provider.baseLanguage))
      }
      val before = file("class Sample {\n  int changed() { return 1; }\n  void removed() {}\n  void stable() {}\n}")
      val after = file("class Sample {\n  int changed() { return 2; }\n  void added() { log(); }\n  void stable() {}\n}")

      val name = StructuralHunkNamer.name(before, after, listOf(HunkLines(1, 3, 1, 3))).single()
      assertEquals(listOf("int changed() { return 2; }" to FileStatus.MODIFIED, "added(): void" to FileStatus.ADDED,
                          "removed(): void" to FileStatus.DELETED),
                   name.parts.map { it.text to it.status })
    }
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

  /** A class with the methods `prefix0()` and on, one on each line. Each method calls the same method. */
  private fun renamedMethods(count: Int, prefix: String): String = buildString {
    append("class Sample {\n")
    for (i in 0 until count) append("  void $prefix$i() { log(); }\n")
    append("  void log() {}\n}")
  }

  /** A plain text file with a Java root, as a template language has. */
  private class JavaTemplateViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    private val javaLanguage: Language,
  ) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, false) {
    override fun getBaseLanguage(): Language = PlainTextLanguage.INSTANCE

    override fun getLanguages(): Set<Language> = setOf(baseLanguage, javaLanguage)

    override fun createFile(language: Language): PsiFile =
      requireNotNull(LanguageParserDefinitions.INSTANCE.forLanguage(language)).createFile(this)

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
      JavaTemplateViewProvider(manager, fileCopy, javaLanguage)
  }
}
