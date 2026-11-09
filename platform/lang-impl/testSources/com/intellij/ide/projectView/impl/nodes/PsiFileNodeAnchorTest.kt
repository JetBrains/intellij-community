// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.LanguageFileViewProviders
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

/**
 * A [PsiFileNode] stores the virtual file of the value, not a smart pointer. A [PsiDirectoryNode] keeps a smart pointer.
 * The tests check the anchor, the restored value, the equality and the fallback to a smart pointer.
 */
@TestApplication
internal class PsiFileNodeAnchorTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project get() = projectFixture.get()

  @Test
  fun `a file node creates no smart pointer tracker`(): Unit = timeoutRunBlocking {
    val file = createFile("tracked.txt")

    val node = fileNode(file)

    assertThat(SmartPointerManagerEx.getInstanceEx(project).getTracker(file)).isNull()
    assertThat(node.equalityObject).isInstanceOf(PsiFileSystemItemAnchor::class.java)
  }

  @Test
  fun `a file node restores its file and matches its virtual file`(): Unit = timeoutRunBlocking {
    val file = createFile("restored.txt")

    val first = fileNode(file)
    val second = fileNode(file)

    readAction {
      assertThat(first.value).isEqualTo(PsiManager.getInstance(project).findFile(file))
      assertThat(first).isEqualTo(second)
      assertThat(first.hashCode()).isEqualTo(second.hashCode())
      assertThat(first.canRepresent(file)).isTrue()
      assertThat(first.contains(file)).isTrue()
    }
  }

  @Test
  fun `a directory node keeps the smart pointer and its path element id`(): Unit = timeoutRunBlocking {
    val directory = createDirectory()

    val node = directoryNode(directory)

    assertThat(node.equalityObject).isInstanceOf(SmartPsiElementPointer::class.java)
    assertThat(node.equalityObject).isNotInstanceOf(PsiFileSystemItemAnchor::class.java)
    assertThat(node.equalityObject.toString()).isEqualTo("dir{$directory}")
    readAction {
      assertThat(node.value).isEqualTo(PsiManager.getInstance(project).findDirectory(directory))
    }
  }

  @Test
  fun `a recreated directory restores again`(): Unit = timeoutRunBlocking {
    val directory = createDirectory()
    val parent = requireNotNull(directory.parent)
    val node = directoryNode(directory)

    edtWriteAction { directory.delete(this) }
    readAction {
      assertThat(node.value).isNull()
    }

    val recreated = edtWriteAction { parent.createChildDirectory(this, directory.name) }
    readAction {
      assertThat(node.value).isNotNull()
      assertThat(node.value).isEqualTo(PsiManager.getInstance(project).findDirectory(recreated))
    }
  }

  @Test
  fun `a deleted file gives a null value`(): Unit = timeoutRunBlocking {
    val file = createFile("deleted.txt")
    val node = fileNode(file)

    edtWriteAction { file.delete(this) }

    readAction {
      assertThat(node.value).isNull()
      assertThat(node.validate()).isFalse()
    }
  }

  @Test
  fun `a light file keeps the smart pointer`(): Unit = timeoutRunBlocking {
    val node = readAction {
      val lightFile = PsiFileFactory.getInstance(project).createFileFromText("light.txt", PlainTextFileType.INSTANCE, "text")
      PsiFileNode(project, lightFile, ViewSettings.DEFAULT)
    }

    assertThat(node.equalityObject).isInstanceOf(SmartPsiElementPointer::class.java)
    assertThat(node.equalityObject).isNotInstanceOf(PsiFileSystemItemAnchor::class.java)
  }

  @Test
  fun `a file renamed to another type gives a null value`(): Unit = timeoutRunBlocking {
    val path = tempPathFixture.get().resolve("Renamed.java")
    path.writeText("class Renamed {}")
    val file = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))
    val node = fileNode(file)
    readAction {
      assertThat(node.value?.language?.id).isEqualTo(JAVA_LANGUAGE_ID)
    }

    edtWriteAction { file.rename(this, "Renamed.txt") }

    readAction {
      assertThat(node.value).isNull()
      assertThat(node.validate()).isFalse()
    }
  }

  @Test
  fun `a node of a non-base root restores that root`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val java = requireNotNull(Language.findLanguageByID(JAVA_LANGUAGE_ID))
    LanguageFileViewProviders.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, FileViewProviderFactory { file, _, manager, eventSystemEnabled ->
      PlainTextAndJavaViewProvider(manager, file, eventSystemEnabled, java)
    }, disposable)
    val file = createFile("twoRoots.txt")

    val node = readAction {
      val javaRoot = requireNotNull(PsiManager.getInstance(project).findFile(file)).viewProvider.getPsi(java)
      PsiFileNode(project, requireNotNull(javaRoot), ViewSettings.DEFAULT)
    }

    readAction {
      val value = requireNotNull(node.value)
      assertThat(value.language).isEqualTo(java)
      assertThat(value).isEqualTo(PsiManager.getInstance(project).findFile(file)!!.viewProvider.getPsi(java))
    }
  }

  @Test
  fun `contains and canRepresent of a deleted item are false`(): Unit = timeoutRunBlocking {
    val directory = createDirectory()
    val childPath = tempPathFixture.get().resolve("kept").resolve("child.txt").createFile()
    val child = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(childPath))
    val directoryNode = directoryNode(directory)
    val fileNode = fileNode(child)

    edtWriteAction { directory.delete(this) }

    readAction {
      assertThat(directoryNode.contains(child)).isFalse()
      assertThat(fileNode.canRepresent(child)).isFalse()
    }
  }

  @Test
  fun `a node built outside a read action gets the anchor`(): Unit = timeoutRunBlocking {
    val file = createFile("background.txt")
    val psiFile = readAction { requireNotNull(PsiManager.getInstance(project).findFile(file)) }

    val node = withContext(Dispatchers.Default) {
      PsiFileNode(project, psiFile, ViewSettings.DEFAULT)
    }

    assertThat(node.equalityObject).isInstanceOf(PsiFileSystemItemAnchor::class.java)
  }

  @Test
  fun `a recreated file restores again`(): Unit = timeoutRunBlocking {
    val file = createFile("recreated.txt")
    val parent = requireNotNull(file.parent)
    val node = fileNode(file)

    edtWriteAction { file.delete(this) }
    readAction {
      assertThat(node.value).isNull()
    }

    val recreated = edtWriteAction { parent.createChildData(this, "recreated.txt") }
    readAction {
      assertThat(node.value).isNotNull()
      assertThat(node.value).isEqualTo(PsiManager.getInstance(project).findFile(recreated))
    }
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempPathFixture.get().resolve(name).createFile()
    return requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))
  }

  private fun createDirectory(): VirtualFile {
    val path = tempPathFixture.get().resolve("kept").createDirectories()
    return requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))
  }

  private suspend fun fileNode(file: VirtualFile): PsiFileNode = readAction {
    PsiFileNode(project, requireNotNull(PsiManager.getInstance(project).findFile(file)), ViewSettings.DEFAULT)
  }

  private suspend fun directoryNode(directory: VirtualFile): PsiDirectoryNode = readAction {
    PsiDirectoryNode(project, requireNotNull(PsiManager.getInstance(project).findDirectory(directory)), ViewSettings.DEFAULT)
  }
}

private const val JAVA_LANGUAGE_ID = "JAVA"

/**
 * A plain text file with a second root in [secondLanguage], as a template language file has.
 */
private class PlainTextAndJavaViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean,
  private val secondLanguage: Language,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled) {
  override fun getBaseLanguage(): Language = PlainTextLanguage.INSTANCE

  override fun getLanguages(): Set<Language> = setOf(PlainTextLanguage.INSTANCE, secondLanguage)

  override fun createFile(lang: Language): PsiFile? = LanguageParserDefinitions.INSTANCE.forLanguage(lang)?.createFile(this)

  override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
    PlainTextAndJavaViewProvider(manager, fileCopy, false, secondLanguage)
}
