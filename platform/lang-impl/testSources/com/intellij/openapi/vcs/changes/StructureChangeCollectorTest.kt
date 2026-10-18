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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class StructureChangeCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `modified files show changed members and omit unchanged members`(): Unit = timeoutRunBlocking {
    val before = """
      class Sample {
        int field = 1;
        int changed() { return 1; }
        void removed() {}
        void unchanged() {}
      }
    """.trimIndent()
    val after = """
      class Sample {
        int field = 2;
        int changed() { return 2; }
        void added() {}
        void unchanged() {}
      }
    """.trimIndent()

    val changes = collect(before, after)
    assertEquals(
      mapOf("field" to FileStatus.MODIFIED, "changed" to FileStatus.MODIFIED,
            "added" to FileStatus.ADDED, "removed" to FileStatus.DELETED),
      changes.associate { it.memberName to it.status },
    )
    assertEquals(after.indexOf("changed()"), changes.single { it.memberName == "changed" }.offset)
    assertEquals(after.indexOf("added()"), changes.single { it.memberName == "added" }.offset)
    assertEquals(after.indexOf("field ="), changes.single { it.memberName == "field" }.offset)
    assertNull(changes.single { it.memberName == "removed" }.offset)
  }

  @Test
  fun `added files show all members`(): Unit = timeoutRunBlocking {
    val after = """
      class Sample {
        int field;
        Sample() {}
        void method() {}
        class Nested {
          void nestedMethod() {}
        }
      }
    """.trimIndent()

    val changes = collect(null, after)
    assertEquals(setOf("field", "Sample", "method", "Nested"), changes.map { it.memberName }.toSet())
    val nested = changes.single { it.memberName == "Nested" }
    assertEquals("nestedMethod", nested.children.single().memberName)
    assertTrue(flatten(changes).all { it.status == FileStatus.ADDED })
    assertTrue(flatten(changes).all { it.offset != null })
  }

  @Test
  fun `unchanged files have no structure changes`(): Unit = timeoutRunBlocking {
    val text = "class Sample { int field; void method() {} }"
    assertTrue(collect(text, text).isEmpty())
  }

  @Test
  fun `overloaded methods keep separate identities`(): Unit = timeoutRunBlocking {
    val before = """
      class Sample {
        int value(int input) { return 1; }
        int value(String input) { return 2; }
      }
    """.trimIndent()
    val after = """
      class Sample {
        int value(int input) { return 3; }
        int value(String input) { return 2; }
        int value(long input) { return 4; }
      }
    """.trimIndent()

    val changes = collect(before, after)
    assertEquals(2, changes.size)
    val modified = changes.single { it.status == FileStatus.MODIFIED }
    val added = changes.single { it.status == FileStatus.ADDED }
    assertEquals("value", modified.memberName)
    assertEquals("value", added.memberName)
    assertTrue(modified.text.contains("int"), modified.text)
    assertTrue(added.text.contains("long"), added.text)
    assertNotEquals(modified.id, added.id)
    assertEquals(after.indexOf("value(int"), modified.offset)
    assertEquals(after.indexOf("value(long"), added.offset)
  }

  @Test
  fun `nested classes keep methods in their own scope`(): Unit = timeoutRunBlocking {
    val before = """
      class Sample {
        class First { int value() { return 1; } }
        class Second { int value() { return 2; } }
      }
    """.trimIndent()
    val after = """
      class Sample {
        class First { int value() { return 1; } }
        class Second { int value() { return 3; } }
      }
    """.trimIndent()

    val nested = collect(before, after).single()
    assertEquals("Second", nested.text)
    assertEquals(FileStatus.NOT_CHANGED, nested.status)
    val method = nested.children.single()
    assertEquals("value", method.memberName)
    assertEquals(FileStatus.MODIFIED, method.status)
    assertEquals(after.lastIndexOf("value()"), method.offset)
  }

  @Test
  fun `renamed methods are added and removed`(): Unit = timeoutRunBlocking {
    val changes = collect("class Sample { void oldName() {} }", "class Sample { void newName() {} }")
    assertEquals(mapOf("oldName" to FileStatus.DELETED, "newName" to FileStatus.ADDED),
                 changes.associate { it.memberName to it.status })
  }

  @Test
  fun `adding the first member and removing the last member keep their changes visible`(): Unit = timeoutRunBlocking {
    val empty = "class Sample {}"
    val withMember = "class Sample { void method() {} }"

    val added = collect(empty, withMember).single()
    assertEquals("method", added.memberName)
    assertEquals(FileStatus.ADDED, added.status)
    assertEquals(withMember.indexOf("method()"), added.offset)

    val removed = collect(withMember, empty).single()
    assertEquals("method", removed.memberName)
    assertEquals(FileStatus.DELETED, removed.status)
    assertEquals(added.id, removed.id)
    assertNull(removed.offset)
  }

  @Test
  fun `changed member types keep their identities`(): Unit = timeoutRunBlocking {
    val before = "class Sample { int field; int value() { return 1; } }"
    val after = "class Sample { long field; long value() { return 1; } }"

    val changes = collect(before, after)
    assertEquals(mapOf("field" to FileStatus.MODIFIED, "value" to FileStatus.MODIFIED),
                 changes.associate { it.memberName to it.status })
    assertEquals(after.indexOf("field;"), changes.single { it.memberName == "field" }.offset)
    assertEquals(after.indexOf("value()"), changes.single { it.memberName == "value" }.offset)
  }

  @Test
  fun `reordered overloads preserve the changed method identity`(): Unit = timeoutRunBlocking {
    val before = """
      class Sample {
        int value(int input) { return 1; }
        int value(String input) { return 2; }
      }
    """.trimIndent()
    val after = """
      class Sample {
        int value(String input) { return 2; }
        int value(int input) { return 3; }
      }
    """.trimIndent()

    val original = collect(null, before).single { it.offset == before.indexOf("value(int") }
    val changed = collect(before, after).single()
    assertEquals(FileStatus.MODIFIED, changed.status)
    assertEquals(original.id, changed.id)
    assertEquals(after.indexOf("value(int"), changed.offset)
  }

  @Test
  fun `added types with matching file names expose their members`(): Unit = timeoutRunBlocking {
    val declarations = mapOf(
      "class Sample { void method() {} }" to setOf("method"),
      "interface Sample { void method(); }" to setOf("method"),
      "enum Sample { FIRST; void method() {} }" to setOf("FIRST", "method"),
      "class Sample<T> { T method() { return null; } }" to setOf("method"),
    )
    for ((text, memberNames) in declarations) {
      val changes = collect(null, text)
      assertEquals(memberNames, changes.map { it.memberName }.toSet(), text)
      assertTrue(changes.all { it.status == FileStatus.ADDED }, text)
      assertEquals(text.indexOf("method()"), changes.single { it.memberName == "method" }.offset, text)
    }
  }

  @Test
  fun `file names that differ from the type keep the type node`(): Unit = timeoutRunBlocking {
    val before = "class Sample { int value() { return 1; } }"
    val after = "class Sample { int value() { return 2; } }"
    val added = collect(null, after, "Other.java").single()
    assertEquals("Sample", added.text)
    assertEquals(FileStatus.ADDED, added.status)
    assertEquals("value", added.children.single().memberName)

    val modified = collect(before, after, "Other.java").single()
    assertEquals("Sample", modified.text)
    assertEquals(FileStatus.NOT_CHANGED, modified.status)
    assertEquals(FileStatus.MODIFIED, modified.children.single().status)
    assertEquals(after.indexOf("value()"), modified.children.single().offset)

    val changedHeader = "final $before"
    val header = collect(before, changedHeader, "Other.java").single()
    assertEquals("Sample", header.text)
    assertEquals(FileStatus.MODIFIED, header.status)
    assertTrue(header.children.isEmpty())
    assertEquals(changedHeader.indexOf("Sample"), header.offset)
  }

  @Test
  fun `unchanged top level types prevent collapsing the matching type`(): Unit = timeoutRunBlocking {
    val before = "class Sample { int value() { return 1; } } class Other {}"
    val after = "class Sample { int value() { return 2; } } class Other {}"
    val owner = collect(before, after).single()
    assertEquals("Sample", owner.text)
    assertEquals(FileStatus.NOT_CHANGED, owner.status)
    assertEquals("value", owner.children.single().memberName)
    assertEquals(FileStatus.MODIFIED, owner.children.single().status)
    assertEquals(after.indexOf("value()"), owner.children.single().offset)

    val added = collect(null, after)
    assertEquals(listOf("Sample", "Other"), added.map { it.text })
    assertTrue(flatten(added).all { it.status == FileStatus.ADDED })
  }

  @Test
  fun `removed top level types prevent collapsing the matching type`(): Unit = timeoutRunBlocking {
    val before = "class Sample { int value() { return 1; } } class Other {}"
    val after = "class Sample { int value() { return 2; } }"
    val changes = collect(before, after)
    assertEquals(mapOf("Sample" to FileStatus.NOT_CHANGED, "Other" to FileStatus.DELETED),
                 changes.associate { it.text to it.status })
    assertEquals(FileStatus.MODIFIED, changes.single { it.text == "Sample" }.children.single().status)
    assertNull(changes.single { it.text == "Other" }.offset)
  }

  @Test
  fun `header changes hide the file container and preserve member identities`(): Unit = timeoutRunBlocking {
    val before = "class Sample { int value() { return 1; } }"
    val changedBody = "class Sample { int value() { return 2; } }"
    val changedHeader = "final class Sample { int value() { return 1; } }"
    val changedBoth = "final class Sample { int value() { return 2; } }"

    assertTrue(collect(before, changedHeader).isEmpty())

    val member = collect(before, changedBoth).single()
    assertEquals("value", member.memberName)
    assertEquals(FileStatus.MODIFIED, member.status)
    assertEquals(collect(before, changedBody).single().id, member.id)
    assertEquals(changedBoth.indexOf("value()"), member.offset)
  }

  @Test
  fun `empty matching types do not duplicate file nodes`(): Unit = timeoutRunBlocking {
    for (text in listOf("class Sample {}", "interface Sample {}", "enum Sample {}")) {
      assertTrue(collect(null, text).isEmpty(), text)
      assertTrue(collect("", text).isEmpty(), text)
    }
  }

  @Test
  fun `matching types added to existing files expose their members`(): Unit = timeoutRunBlocking {
    val text = "class Sample { void method() {} }"
    val member = collect("", text).single()
    assertEquals("method", member.memberName)
    assertEquals(FileStatus.ADDED, member.status)
    assertEquals(collect(null, text).single().id, member.id)
    assertEquals(text.indexOf("method()"), member.offset)
  }

  @Test
  fun `header changes in an empty matching class do not duplicate the file node`(): Unit = timeoutRunBlocking {
    val before = "class HeaderChange {}"
    val after = "final class HeaderChange {}"

    assertTrue(collect(before, after, "HeaderChange.java").isEmpty())
  }

  @Test
  fun `nested header changes keep the nested class node`(): Unit = timeoutRunBlocking {
    val before = "class Sample { class Nested {} }"
    val after = "class Sample { final class Nested {} }"

    val nested = collect(before, after).single()
    assertEquals("Nested", nested.text)
    assertEquals(FileStatus.MODIFIED, nested.status)
    assertTrue(nested.children.isEmpty())
    assertEquals(after.indexOf("Nested"), nested.offset)
  }

  @Test
  fun `renamed types retain added and removed nodes`(): Unit = timeoutRunBlocking {
    for ((oldName, newName) in listOf("Sample" to "Renamed", "Previous" to "Sample")) {
      val before = "class $oldName { void method() {} }"
      val after = "class $newName { void method() {} }"
      val changes = collect(before, after)
      assertEquals(mapOf(oldName to FileStatus.DELETED, newName to FileStatus.ADDED),
                   changes.associate { it.text to it.status })
      val added = changes.single { it.text == newName }
      assertEquals(after.indexOf(newName), added.offset)
      assertEquals(FileStatus.ADDED, added.children.single().status)
      assertEquals(after.indexOf("method()"), added.children.single().offset)
      val removed = changes.single { it.text == oldName }
      assertNull(removed.offset)
      assertEquals(FileStatus.DELETED, removed.children.single().status)
      assertNull(removed.children.single().offset)
      assertNotEquals(added.id, removed.id)
    }
  }

  @Test
  fun `snapshots retain the file context without changing its content`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    smartReadAction(project) {
      val factory = PsiFileFactory.getInstance(project)
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName("Sample.java")
      val originalText = "class Sample { int value() { return 1; } }"
      val changedText = "class Sample { int value() { return 2; } }"
      val original = factory.createFileFromText("Sample.java", fileType, originalText, 0, true)
      val before = requireNotNull(StructureChangeCollector.createSnapshot(original, originalText))
      val after = requireNotNull(StructureChangeCollector.createSnapshot(original, changedText))

      assertSame(original, before.originalFile)
      assertSame(original, after.originalFile)
      assertNotSame(original.virtualFile, requireNotNull(before.virtualFile))
      assertNotSame(original.virtualFile, requireNotNull(after.virtualFile))
      assertNotSame(before.virtualFile, after.virtualFile)
      assertEquals(originalText, original.text)
      assertEquals(originalText, before.text)
      assertEquals(changedText, after.text)
      val change = StructureChangeCollector.collect(before, after).single()
      assertEquals("value", change.memberName)
      assertEquals(FileStatus.MODIFIED, change.status)
      assertEquals(changedText.indexOf("value()"), change.offset)
    }
  }

  @Test
  fun `secondary PSI roots retain their structural changes`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    val javaLanguage = requireNotNull(Language.findLanguageByID("JAVA"))
    val builders = LanguageStructureViewBuilder.getInstance()
    builders.addExplicitExtension(PlainTextLanguage.INSTANCE, PsiStructureViewFactory { file ->
      builders.getStructureViewBuilder(requireNotNull(file.viewProvider.getPsi(javaLanguage)))
    }, disposable)
    smartReadAction(project) {
      val beforeText = "class Sample { int changed() { return 1; } void removed() {} void stable() {} }"
      val afterText = "class Sample { int changed() { return 2; } void added() {} void stable() {} }"
      val manager = PsiManager.getInstance(project)
      fun file(text: String): PsiFile {
        val provider = JavaTemplateViewProvider(manager, LightVirtualFile("Sample.txt", PlainTextFileType.INSTANCE, text), javaLanguage)
        val root = requireNotNull(provider.getPsi(provider.baseLanguage))
        val javaRoot = requireNotNull(provider.getPsi(javaLanguage))
        assertNotSame(root, javaRoot)
        assertSame(root.viewProvider, javaRoot.viewProvider)
        return root
      }

      val before = file(beforeText)
      val after = file(afterText)
      val owner = StructureChangeCollector.collect(before, after).single()
      assertEquals("Sample", owner.text)
      assertEquals(FileStatus.NOT_CHANGED, owner.status)
      assertEquals(mapOf("changed" to FileStatus.MODIFIED, "added" to FileStatus.ADDED, "removed" to FileStatus.DELETED),
                   owner.children.associate { it.memberName to it.status })
      assertEquals(afterText.indexOf("changed()"), owner.children.single { it.memberName == "changed" }.offset)
      assertEquals(afterText.indexOf("added()"), owner.children.single { it.memberName == "added" }.offset)
      assertNull(owner.children.single { it.memberName == "removed" }.offset)

      val added = StructureChangeCollector.collect(null, after).single()
      assertEquals(setOf("changed", "added", "stable"), added.children.map { it.memberName }.toSet())
      assertTrue(flatten(listOf(added)).all { it.status == FileStatus.ADDED })
    }
  }

  private suspend fun collect(before: String?, after: String, fileName: String = "Sample.java"): List<StructureChange> {
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    return smartReadAction(project) {
      val factory = PsiFileFactory.getInstance(project)
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
      val afterFile = factory.createFileFromText(fileName, fileType, after)
      val beforeFile = before?.let { factory.createFileFromText(fileName, fileType, it) }
      StructureChangeCollector.collect(beforeFile, afterFile)
    }
  }

  private val StructureChange.memberName: String
    get() = text.substringBefore('(').substringBefore(':').trim()

  private fun flatten(changes: List<StructureChange>): List<StructureChange> =
    changes.flatMap { listOf(it) + flatten(it.children) }

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
