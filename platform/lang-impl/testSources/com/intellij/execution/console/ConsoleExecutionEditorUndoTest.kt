// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier

/**
 * A command that changes a physical file while a console is current puts the console file into the undo history of that file.
 * A disposed console must leave no reference to its file there.
 */
@TestApplication
internal class ConsoleExecutionEditorUndoTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
  }

  private val physicalFile = sourceRoot.virtualFileFixture("physical.txt", "")

  @AfterEach
  fun dropHistory() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      undoManager().dropHistoryInTests()
    }
  }

  @Test
  fun `a disposed console leaves its file in no undo history of another document`() {
    val physical = timeoutRunBlocking(context = Dispatchers.EDT) {
      checkNotNull(FileDocumentManager.getInstance().getDocument(physicalFile.get()))
    }
    val consoleFile = timeoutRunBlocking(context = Dispatchers.EDT) {
      val console = LanguageConsoleImpl(project.get(), "Undo console", PlainTextLanguage.INSTANCE)
      try {
        changeWhileConsoleIsCurrent(console, physical)
      } finally {
        Disposer.dispose(console)
      }
      console.virtualFile
    }
    val roots = Supplier {
      val result = LeakHunter.allRoots().get()
      result[physical] = "the changed document"
      result
    }
    // The code style cache keeps a copy of a light file under the same name, so the check compares the instance.
    LeakHunter.checkLeak(roots, LightVirtualFile::class.java) { file ->
      file === consoleFile
    }
  }

  private fun changeWhileConsoleIsCurrent(console: LanguageConsoleImpl, changed: Document) {
    val undoManager = undoManager()
    val textEditor = TextEditorProvider.getInstance().getTextEditor(console.consoleEditor)
    val provider = object : CurrentEditorProvider {
      override fun getCurrentEditor(project: Project?): FileEditor = textEditor
    }
    undoManager.setOverriddenEditorProvider(provider)
    try {
      val command = Runnable {
        ApplicationManager.getApplication().runWriteAction {
          changed.insertString(0, " ")
        }
      }
      CommandProcessor.getInstance().executeCommand(project.get(), command, "type", null)
    } finally {
      undoManager.setOverriddenEditorProvider(null)
    }
    undoManager.flushCurrentCommandMerger()
  }

  private fun undoManager(): UndoManagerImpl {
    return UndoManager.getInstance(project.get()) as UndoManagerImpl
  }
}
