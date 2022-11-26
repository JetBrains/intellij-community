// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.isCurrentlyUnderLocalId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSessionsManager.Companion.getProjectSession
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.IncorrectOperationException
import org.jdom.Element
import java.awt.Component
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLabel

internal class TestEditorManagerImpl(private val project: Project) : FileEditorManagerEx(), Disposable {
  companion object {
    private val LOG = Logger.getInstance(TestEditorManagerImpl::class.java)
    private val LIGHT_VIRTUAL_FILE = MyLightVirtualFile()
    private val provider: FileEditorProvider
      get() = object : FileEditorProvider {
        override fun accept(project: Project, file: VirtualFile) = false

        override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw IncorrectOperationException()

        override fun disposeEditor(editor: FileEditor) {}
        override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
          throw IncorrectOperationException()
        }

        override fun getEditorTypeId() = ""

        override fun getPolicy(): FileEditorPolicy = throw IncorrectOperationException()
      }
  }

  private val testEditorSplitter = TestEditorSplitter()
  private var counter = 0
  private val virtualFileToEditor = HashMap<VirtualFile, Editor?>()
  private var activeFile: VirtualFile? = null

  private class MyLightVirtualFile() : LightVirtualFile("Dummy.java") {
    fun clearUserDataOnDispose() {
      clearUserData()
    }
  }

  init {
    registerExtraEditorDataProvider(TextEditorPsiDataProvider(), null)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        if (this@TestEditorManagerImpl.project === project) {
          closeAllFiles()
        }
      }
    })
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileDeleteEvent) {
            for (file in openFiles) {
              if (VfsUtilCore.isAncestor(event.file, file, false)) {
                closeFile(file)
              }
            }
          }
        }
      }
    })
  }

  override fun openFileWithProviders(file: VirtualFile,
                                     focusEditor: Boolean,
                                     searchForSplitter: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileInCommand(OpenFileDescriptor(project, file))
  }

  private fun openFileImpl3(openFileDescriptor: FileEditorNavigatable): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    val file = openFileDescriptor.file
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager
                          ?: return Pair.createNonNull(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY)
      val result = clientManager.openFile(file, false)
      val fileEditors = result.map { it.fileEditor }.toTypedArray()
      val providers = result.map { it.provider }.toTypedArray()
      return Pair.createNonNull(fileEditors, providers)
    }
    val isNewEditor = !virtualFileToEditor.containsKey(file)

    // for non-text editors. uml, etc
    var provider = file.getUserData(FileEditorProvider.KEY)
    val result: Pair<Array<FileEditor>, Array<FileEditorProvider>>
    val fileEditor: FileEditor
    val editor: Editor?
    if (provider != null && provider.accept(project, file)) {
      fileEditor = provider.createEditor(project, file)
      if (fileEditor is TextEditor) {
        editor = fileEditor.editor
        TextEditorProvider.putTextEditor(editor, fileEditor)
      }
      else {
        editor = null
      }
    }
    else {
      //text editor
      editor = doOpenTextEditor(openFileDescriptor)
      fileEditor = TextEditorProvider.getInstance().getTextEditor(editor)
      provider = Companion.provider
    }
    result = Pair.create(arrayOf(fileEditor), arrayOf(provider))
    virtualFileToEditor.put(file, editor)
    activeFile = file
    if (editor != null) {
      editor.selectionModel.removeSelection()
      if (openFileDescriptor is OpenFileDescriptor) {
        openFileDescriptor.navigateIn(editor)
      }
    }
    modifyTabWell {
      testEditorSplitter.openAndFocusTab(file, result.first[0], result.second[0])
      if (isNewEditor) {
        eventPublisher().fileOpened(this, file)
      }
    }
    return result
  }

  private fun modifyTabWell(tabWellModification: Runnable) {
    if (project.isDisposed) {
      return
    }

    val lastFocusedEditor = testEditorSplitter.focusedFileEditor
    val lastFocusedFile = testEditorSplitter.focusedFile
    val oldProvider = testEditorSplitter.providerFromFocused
    tabWellModification.run()
    val currentlyFocusedEditor = testEditorSplitter.focusedFileEditor
    val currentlyFocusedFile = testEditorSplitter.focusedFile
    val newProvider = testEditorSplitter.providerFromFocused
    val event = FileEditorManagerEvent(this, lastFocusedFile, lastFocusedEditor, oldProvider, currentlyFocusedFile, currentlyFocusedEditor,
                                       newProvider)
    eventPublisher().selectionChanged(event)
  }

  private fun eventPublisher(): FileEditorManagerListener {
    return project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
  }

  override fun openFileWithProviders(file: VirtualFile,
                                     focusEditor: Boolean,
                                     window: EditorWindow): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileWithProviders(file, focusEditor, false)
  }

  override fun isInsideChange() = false

  override fun notifyPublisher(runnable: Runnable) {
    runnable.run()
  }

  override fun getSplittersFor(c: Component): EditorsSplitters? = null

  override fun createSplitter(orientation: Int, window: EditorWindow?) {
    val containerName = createNewTabbedContainerName()
    testEditorSplitter.setActiveTabGroup(containerName)
  }

  private fun createNewTabbedContainerName(): String {
    counter++
    return "SplitTabContainer$counter"
  }

  override fun changeSplitterOrientation() {}

  override fun isInSplitter() = false

  override fun hasOpenedFile() = false

  override fun getCurrentFile(): VirtualFile? {
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return null
      return clientManager.getSelectedFile()
    }
    return activeFile
  }

  private val clientFileEditorManager: ClientFileEditorManager?
    get() {
      val clientId = current
      LOG.assertTrue(!clientId.isLocal, "Trying to get ClientFileEditorManager for local ClientId")
      val session = getProjectSession(project, clientId)
      return session?.getService(ClientFileEditorManager::class.java)
    }

  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? {
    val editor = getEditor(file)
    if (editor != null) {
      val textEditorProvider = TextEditorProvider.getInstance()
      val provider = Optional.ofNullable(editor.getUserData(FileEditorProvider.KEY)).orElse(textEditorProvider)
      val fileEditor = textEditorProvider.getTextEditor(editor)
      return FileEditorWithProvider(fileEditor, provider)
    }
    return null
  }

  override fun isChanged(editor: EditorComposite) = false

  override fun getNextWindow(window: EditorWindow): EditorWindow? = null

  override fun getPrevWindow(window: EditorWindow): EditorWindow? = null

  override fun addTopComponent(editor: FileEditor, component: JComponent) {}
  override fun removeTopComponent(editor: FileEditor, component: JComponent) {}
  override fun addBottomComponent(editor: FileEditor, component: JComponent) {}
  override fun removeBottomComponent(editor: FileEditor, component: JComponent) {}

  override fun closeAllFiles() {
    for (file in openFiles) {
      closeFile(file)
    }
  }

  override fun getCurrentWindow(): EditorWindow? = null

  override fun getActiveWindow(): CompletableFuture<EditorWindow?> = CompletableFuture.completedFuture(null)

  override fun setCurrentWindow(window: EditorWindow) {}

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun getFile(editor: FileEditor): VirtualFile = LIGHT_VIRTUAL_FILE

  override fun unsplitWindow() {}

  override fun unsplitAllWindow() {}

  override fun getWindows(): Array<EditorWindow> = emptyArray()

  override fun getSelectedEditor(file: VirtualFile): FileEditor? {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditor()
    }

    getEditor(file)?.let { return TextEditorProvider.getInstance().getTextEditor(it) }
    return testEditorSplitter.getEditorAndProvider(file)?.first
  }

  override fun getSelectedEditorWithRemotes(): Array<FileEditor> {
    val result: MutableList<FileEditor> = ArrayList()
    Collections.addAll(result, *selectedEditors)
    for (m in allClientFileEditorManagers) {
      result.addAll(m.getSelectedEditors())
    }
    return result.toTypedArray()
  }

  override fun isFileOpen(file: VirtualFile): Boolean {
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return false
      return clientManager.isFileOpen(file)
    }
    return getEditor(file) != null
  }

  override fun isFileOpenWithRemotes(file: VirtualFile): Boolean {
    if (isFileOpen(file)) {
      return true
    }
    return allClientFileEditorManagers.any { it.isFileOpen(file) }
  }

  override fun getEditors(file: VirtualFile): Array<FileEditor> {
    return arrayOf(getSelectedEditor(file) ?: return FileEditor.EMPTY_ARRAY)
  }

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> {
    val result = ArrayList<FileEditor>()
    result.addAll(getEditors(file))
    for (clientManager in allClientFileEditorManagers) {
      result.addAll(clientManager.getEditors(file))
    }
    return result.toArray(FileEditor.EMPTY_ARRAY)
  }

  override fun getOpenFilesWithRemotes(): List<VirtualFile> {
    return (openFiles.asSequence() + allClientFileEditorManagers.asSequence().flatMap { it.getAllFiles() }).toList()
  }

  override fun getSiblings(file: VirtualFile): List<VirtualFile> = throw UnsupportedOperationException()

  override fun dispose() {
    closeAllFiles()
    LIGHT_VIRTUAL_FILE.clearUserDataOnDispose()
  }

  override fun closeFile(file: VirtualFile) {
    val editor = virtualFileToEditor.remove(file)
    if (editor != null) {
      val editorProvider = TextEditorProvider.getInstance()
      editorProvider.disposeEditor(editorProvider.getTextEditor(editor))
      if (!editor.isDisposed) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
      if (!project.isDisposed) {
        eventPublisher().fileClosed(this, file)
      }
    }
    if (file == activeFile) {
      activeFile = null
    }
    modifyTabWell { testEditorSplitter.closeFile(file) }
  }

  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    closeFile(file)
  }

  override fun getSelectedFiles(): Array<VirtualFile> {
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return VirtualFile.EMPTY_ARRAY
      return clientManager.getSelectedFiles().toTypedArray()
    }
    return if (activeFile == null) VirtualFile.EMPTY_ARRAY else arrayOf(activeFile!!)
  }

  override fun getSelectedEditors(): Array<FileEditor> {
    if (!isCurrentlyUnderLocalId) {
      return (clientFileEditorManager ?: return FileEditor.EMPTY_ARRAY).getSelectedEditors().toTypedArray()
    }
    return if (activeFile == null) FileEditor.EMPTY_ARRAY else getEditors(activeFile!!)
  }

  override fun getSelectedTextEditor(): Editor? {
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return null
      val selectedEditor = clientManager.getSelectedEditor()
      return if (selectedEditor is TextEditor) selectedEditor.editor else null
    }
    return IntentionPreviewUtils.getPreviewEditor() ?: getEditor(activeFile ?: return  null)
  }

  override fun getSelectedTextEditorWithRemotes(): Array<Editor> {
    val result = ArrayList<Editor>()
    for (e in selectedEditorWithRemotes) {
      if (e is TextEditor) {
        result.add(e.editor)
      }
    }
    return result.toTypedArray()
  }

  override fun getComponent(): JComponent = JLabel()

  override fun getOpenFiles(): Array<VirtualFile> {
    if (!isCurrentlyUnderLocalId) {
      return (clientFileEditorManager ?: return VirtualFile.EMPTY_ARRAY).getAllFiles().toTypedArray()
    }
    return VfsUtilCore.toVirtualFileArray(virtualFileToEditor.keys)
  }

  fun getEditor(file: VirtualFile): Editor? = virtualFileToEditor.get(file)

  override fun getAllEditors(): Array<FileEditor> {
    val result = ArrayList<FileEditor>()
    for ((_, value) in virtualFileToEditor) {
      result.add(TextEditorProvider.getInstance().getTextEditor(value!!))
    }
    for (clientManager in allClientFileEditorManagers) {
      result.addAll(clientManager.getAllEditors())
    }
    return result.toTypedArray()
  }

  private val allClientFileEditorManagers: List<ClientFileEditorManager>
    get() = project.getServices(ClientFileEditorManager::class.java, false)

  override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? {
    val pair = openFileInCommand(descriptor)
    for (editor in pair.first) {
      if (editor is TextEditor) {
        return editor.editor
      }
    }
    return null
  }

  private fun openFileInCommand(descriptor: FileEditorNavigatable): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    var result: Pair<Array<FileEditor>, Array<FileEditorProvider>>? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val editWithProvider = openFileImpl3(descriptor)
      val editors = editWithProvider.first
      for (i in editors.indices) {
        val editor = editors[i]
        if (editor is NavigatableFileEditor && descriptor.file == editor.file) {
          if (editor.canNavigateTo(descriptor)) {
            setSelectedEditor(descriptor.file, editWithProvider.second[i].editorTypeId)
            editor.navigateTo(descriptor)
          }
          break
        }
      }
      result = editWithProvider
    }, "", null)
    return result!!
  }

  private fun doOpenTextEditor(descriptor: FileEditorNavigatable): Editor {
    val file = descriptor.file
    virtualFileToEditor.get(file)?.let { return it }
    val document = FileDocumentManager.getInstance().getDocument(file)
    LOG.assertTrue(document != null, file)
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(document!!, project)
    try {
      val highlighter = HighlighterFactory.createHighlighter(project, file)
      val language = TextEditorImpl.getDocumentLanguage(editor)
      editor.settings.setLanguageSupplier { language }
      val editorEx = editor as EditorEx?
      editorEx!!.highlighter = highlighter
      editorEx.setFile(file)
    }
    catch (e: Throwable) {
      editorFactory.releaseEditor(editor)
      throw e
    }
    return editor!!
  }

  override fun openFileEditor(descriptor: FileEditorNavigatable, focusEditor: Boolean): List<FileEditor> {
    val pair = openFileInCommand(descriptor)
    return pair.first.asList()
  }

  override fun getProject() = project

  override fun getPreferredFocusedComponent(): JComponent = throw UnsupportedOperationException()

  override fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return EditorComposite.retrofit(getComposite(file))
  }

  override fun getComposite(file: VirtualFile): FileEditorComposite? {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getComposite(file)
    }

    return testEditorSplitter.getEditorAndProvider(file)?.let {
      TestEditorComposite(it.first, it.second)
    }
  }

  override fun getWindowSplitCount() = 0

  override fun hasSplitOrUndockedWindows() = false

  override fun getSplitters(): EditorsSplitters = throw IncorrectOperationException()

  override fun getReady(requestor: Any) = ActionCallback.DONE

  override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) {
    if (!isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager
      clientManager?.setSelectedEditor(file, fileEditorProviderId)
      return
    }

    if (virtualFileToEditor.containsKey(file)) {
      modifyTabWell {
        activeFile = file
        testEditorSplitter.focusedFile = file
      }
    }
  }
}

private data class TestEditorComposite(val editor: FileEditor, val provider: FileEditorProvider) : FileEditorComposite {
  override val allEditors: List<FileEditor>
    get() = listOf(editor)
  override val allProviders: List<FileEditorProvider>
    get() = listOf(provider)
  override val isPreview: Boolean
    get() = false
}
