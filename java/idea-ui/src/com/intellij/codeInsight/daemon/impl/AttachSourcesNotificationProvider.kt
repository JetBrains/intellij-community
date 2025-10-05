// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.CommonBundle
import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.codeInsight.AttachSourcesProvider.LightAttachSourcesAction
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSourceAttachCollector.onSourcesAttached
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsParsingUtil
import com.intellij.psi.util.JavaMultiReleaseUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.GuiUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.SwingUtilities

private val EP_NAME: ExtensionPointName<AttachSourcesProvider> = ExtensionPointName("com.intellij.attachSourcesProvider")

class AttachSourcesNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent>? {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) return null

    val classFileInfo = getTextWithClassFileInfo(file)
    val notificationPanelCreator = Function { fileEditor: FileEditor? ->
      EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).text(classFileInfo)
    }

    val sourceFile = JavaEditorFileSwapper.findSourceFile(project, file)
    if (sourceFile != null) {
      return notificationPanelCreator.andThen { panel ->
        appendOpenFileAction(project, panel, sourceFile, JavaUiBundle.message("class.file.open.source.action"))
        panel
      }
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)
    val baseFile = JavaMultiReleaseUtil.findBaseFile(file)
    if (baseFile != null) {
      val baseSource = JavaEditorFileSwapper.findSourceFile(project, baseFile)
      if (baseSource != null) {
        return notificationPanelCreator.andThen { panel ->
          appendOpenFileAction(project, panel, baseSource, JavaUiBundle.message("class.file.open.source.version.specific.action"))
          panel
        }
      }
    }

    val libraries = ProjectFileIndex.getInstance(project).getOrderEntriesForFile(file).filterIsInstance<LibraryOrderEntry>()
    val actions = if (psiFile != null) collectActions(libraries, psiFile) else mutableListOf()

    val sourceFileIsInSameJar = sourceFileIsInSameJar(file)

    return notificationPanelCreator.andThen { panel ->
      if (libraries.isNotEmpty()) {
        val defaultAction = if (sourceFileIsInSameJar) AttachJarAsSourcesAction(file) else ChooseAndAttachSourcesAction(project, panel)
        actions.add(defaultAction)
      }

      for (action in actions) {
        panel.createActionLabel(GuiUtils.getTextWithoutMnemonicEscaping(action.getName())) {
          project.service<CoroutineScopeHolder>().coroutineScope.launch {
            checkCanceled()
            val originalText = panel.text
            val started = System.currentTimeMillis()

            runCatching {
              panel.text = action.getBusyText()
              action.perform(libraries)
            }.onFailure {
              SwingUtilities.invokeLater {
                Messages.showErrorDialog(project, it.localizedMessage, CommonBundle.message("title.error"))
              }
            }.onSuccess {
              it.doWhenProcessed {
                if (psiFile != null) {
                  onSourcesAttached(project, action.javaClass, psiFile.getLanguage(), it.isDone, System.currentTimeMillis() - started)
                }
              }
            }
            panel.text = originalText
          }
        }
      }

      panel
    }
  }

  private fun sourceFileIsInSameJar(classFile: VirtualFile): Boolean {
    var name = classFile.getName()
    var i = name.indexOf('$')
    if (i != -1) name = name.take(i)
    i = name.indexOf('.')
    if (i != -1) name = name.take(i)
    return classFile.getParent().findChild(name + JavaFileType.DOT_DEFAULT_EXTENSION) != null
  }

  private fun collectActions(libraries: List<LibraryOrderEntry>, classFile: PsiFile): MutableList<AttachSourcesAction> {
    val actions = mutableListOf<AttachSourcesAction>()

    var hasNonLightAction = false
    EP_NAME.forEachExtensionSafe { provider ->
      if (!provider.isApplicable(libraries, classFile)) return@forEachExtensionSafe

      for (action in provider.getActions(libraries, classFile)) {
        when {
          hasNonLightAction && action is LightAttachSourcesAction -> continue  // Don't add LightAttachSourcesAction if non-light action exists.
          action !is LightAttachSourcesAction -> {
            actions.clear() // All previous actions is LightAttachSourcesAction and should be removed.
            hasNonLightAction = true
          }
        }
        actions.add(action)
      }
    }

    actions.sortBy { it.name.lowercase() }
    return actions
  }

  @RequiresBackgroundThread
  private fun getTextWithClassFileInfo(file: VirtualFile): @NlsContexts.Label String {
    val level = JavaMultiReleaseUtil.getVersion(file)

    @Nls val info = StringBuilder()
    if (level != null) {
      info.append(JavaUiBundle.message("class.file.multi.release.decompiled.text", level.feature()))
    }
    else {
      info.append(JavaUiBundle.message("class.file.decompiled.text"))
    }

    try {
      val data = file.contentsToByteArray(false)
      if (data.size > 8) {
        DataInputStream(ByteArrayInputStream(data)).use { stream ->
          if (stream.readInt() == 0xCAFEBABE.toInt()) {
            val minor = stream.readUnsignedShort()
            val major = stream.readUnsignedShort()
            info.append(", ").append(JavaUiBundle.message("class.file.decompiled.bytecode.version.text", major, minor))

            val sdkVersion = ClsParsingUtil.getJdkVersionByBytecode(major)
            if (sdkVersion != null) {
              info.append(" ").append(JavaUiBundle.message("class.file.decompiled.sdk.version.text", getSdkDescription(sdkVersion, ClsParsingUtil.isPreviewLevel(minor))))
            }
          }
        }
      }
    }
    catch (_: IOException) {
    }

    return info.toString()
  }

  private fun appendOpenFileAction(project: Project, panel: EditorNotificationPanel, sourceFile: VirtualFile, @Nls title: String) {
    panel.createActionLabel(title) {
      if (sourceFile.isValid()) {
        val descriptor = OpenFileDescriptor(project, sourceFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
      }
    }
  }

  private fun getSdkDescription(
    sdkVersion: JavaSdkVersion,
    isPreview: Boolean,
  ): String = sdkVersion.description + (if (sdkVersion.isAtLeast(JavaSdkVersion.JDK_11) && isPreview) "-preview" else "")

  internal class AttachJarAsSourcesAction(private val myClassFile: VirtualFile) : AttachSourcesAction {
    override fun getName(): String = JavaUiBundle.message("module.libraries.attach.sources.button")

    override fun getBusyText(): String = JavaUiBundle.message("library.attach.sources.action.busy.text")

    override fun perform(orderEntriesContainingFile: MutableList<out LibraryOrderEntry>): ActionCallback {
      val modelsToCommit = mutableListOf<Library.ModifiableModel>()

      for (orderEntry in orderEntriesContainingFile) {
        val library = orderEntry.getLibrary() ?: continue
        val root = findRoot(library) ?: continue

        val model = library.getModifiableModel()
        model.addRoot(root, OrderRootType.SOURCES)
        modelsToCommit.add(model)
      }
      if (modelsToCommit.isEmpty()) return ActionCallback.REJECTED
      runWriteAction {
        for (model in modelsToCommit) {
          model.commit()
        }
      }

      return ActionCallback.DONE
    }

    fun findRoot(library: Library): VirtualFile? {
      for (classesRoot in library.getFiles(OrderRootType.CLASSES)) {
        if (VfsUtilCore.isAncestor(classesRoot, myClassFile, true)) {
          return classesRoot
        }
      }
      return null
    }
  }

  internal class ChooseAndAttachSourcesAction(
    private val myProject: Project,
    private val myParentComponent: JComponent,
  ) : AttachSourcesAction {
    override fun getName(): String = JavaUiBundle.message("module.libraries.choose.sources.button")

    override fun getBusyText(): String = JavaUiBundle.message("library.attach.sources.action.busy.text")

    override fun perform(libraries: MutableList<out LibraryOrderEntry>): ActionCallback {
      val descriptor = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor()
      descriptor.title = JavaUiBundle.message("library.attach.sources.action")
      descriptor.description = JavaUiBundle.message("library.attach.sources.description")
      val firstLibrary = libraries[0].library

      val roots = firstLibrary?.getFiles(OrderRootType.CLASSES) ?: VirtualFile.EMPTY_ARRAY
      val candidates = FileChooser.chooseFiles(descriptor, myProject, if (roots.size == 0) null else VfsUtil.getLocalFile(roots[0]!!))
      if (candidates.size == 0) return ActionCallback.REJECTED
      val files = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(myParentComponent, candidates)
      if (files.size == 0) return ActionCallback.REJECTED

      val librariesToAppendSourcesTo = mutableMapOf<Library?, LibraryOrderEntry?>()
      for (library in libraries) {
        librariesToAppendSourcesTo[library.library] = library
      }
      if (librariesToAppendSourcesTo.size == 1) {
        appendSources(firstLibrary!!, files)
      }
      else {
        librariesToAppendSourcesTo[null] = null

        val title = JavaUiBundle.message("library.choose.one.to.attach")
        val entries: MutableList<LibraryOrderEntry?> = ArrayList(librariesToAppendSourcesTo.values)

        JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<LibraryOrderEntry?>(title, entries) {
          override fun getSeparatorAbove(value: LibraryOrderEntry?): ListSeparator? {
            return if (value == null) ListSeparator() else null
          }

          override fun getTextFor(value: LibraryOrderEntry?): String {
            return if (value == null) CommonBundle.message("action.text.all")
            else value.getPresentableName() + " (" + value.getOwnerModule().getName() + ")"
          }

          override fun onChosen(libraryOrderEntry: LibraryOrderEntry?, finalChoice: Boolean): PopupStep<*>? {
            if (libraryOrderEntry != null) {
              appendSources(libraryOrderEntry.getLibrary()!!, files)
            }
            else {
              for (libOrderEntry in librariesToAppendSourcesTo.keys) {
                if (libOrderEntry != null) {
                  appendSources(libOrderEntry, files)
                }
              }
            }
            return FINAL_CHOICE
          }
        }).showCenteredInCurrentWindow(myProject)
      }

      return ActionCallback.DONE
    }

    companion object {
      private fun appendSources(library: Library, files: Array<VirtualFile>) {
        ApplicationManager.getApplication().runWriteAction(Runnable {
          val model = library.getModifiableModel()
          for (virtualFile in files) {
            model.addRoot(virtualFile, OrderRootType.SOURCES)
          }
          model.commit()
        })
      }
    }
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeHolder(val coroutineScope: CoroutineScope)

}
