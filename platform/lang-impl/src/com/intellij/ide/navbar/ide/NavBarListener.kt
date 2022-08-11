// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ProjectTopics
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.actions.CutAction
import com.intellij.ide.navigationToolbar.NavBarActions
import com.intellij.ide.navigationToolbar.NavBarModelListener
import com.intellij.ide.navbar.ide.NavBarEvent.ModelChangeEvent
import com.intellij.ide.navbar.ide.NavBarEvent.PresentationChangeEvent
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.PopupAction
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.ui.ListActions
import com.intellij.ui.ScrollingUtil.ScrollingAction
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.Nls
import java.awt.KeyboardFocusManager
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener


class NavBarListener(private val myProject: Project,
                     private val myEventFlow: MutableSharedFlow<NavBarEvent>)
  : AdditionalLibraryRootsListener,
    AnActionListener,
    DynamicPluginListener,
    FileEditorManagerListener,
    FileStatusListener,
    FocusListener,
    KeyListener,
    LafManagerListener,
    ModuleRootListener,
    NavBarModelListener,
    ProblemListener,
    PropertyChangeListener,
    PsiTreeChangeListener,
    VirtualFileAppearanceListener,
    WindowFocusListener,

    Disposable {

  private var shouldFocusEditor = false


  init {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(this)
    FileStatusManager.getInstance(myProject).addFileStatusListener(this)
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(this)

    val connection = myProject.messageBus.connect()
    Disposer.register(this, connection)

    connection.subscribe(AnActionListener.TOPIC, this)
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, this)
    connection.subscribe(NavBarModelListener.NAV_BAR, this)
    connection.subscribe(ProblemListener.TOPIC, this)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    connection.subscribe(DynamicPluginListener.TOPIC, this)
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, this)
    //myPanel.addFocusListener(this)
    //if (myPanel.allowNavItemsFocus()) {
    //  myPanel.addNavBarItemFocusListener(this)
    //}

    //emitModelChange()
  }

  override fun dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(this)
    FileStatusManager.getInstance(myProject).removeFileStatusListener(this)
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(this)
  }

  override fun focusGained(e: FocusEvent) {
    emitModelChange()

    //if (myPanel.allowNavItemsFocus()) {
    //  // If focus comes from anything in the nav bar panel, ignore the event
    //  if (UIUtil.isAncestor(myPanel, e.oppositeComponent)) {
    //    return
    //  }
    //}
    //if (e.oppositeComponent == null && shouldFocusEditor) {
    //  shouldFocusEditor = false
    //  getInstance(myPanel.project).activateEditorComponent()
    //  return
    //}
    //myPanel.updateItems()
    //val items = myPanel.items
    //if (!myPanel.isInFloatingMode && items.size > 0) {
    //  myPanel.setContextComponent(items[items.size - 1])
    //}
    //else {
    //  myPanel.setContextComponent(null)
    //}
  }

  override fun focusLost(e: FocusEvent) {
    emitModelChange()

    //if (myPanel.allowNavItemsFocus()) {
    //  // If focus reaches anything in nav bar panel, ignore the event
    //  if (UIUtil.isAncestor(myPanel, e.oppositeComponent)) {
    //    return
    //  }
    //}
    //if (myPanel.project.isDisposed) {
    //  myPanel.setContextComponent(null)
    //  myPanel.hideHint()
    //  return
    //}
    //val dialog = DialogWrapper.findInstance(e.oppositeComponent)
    //shouldFocusEditor = dialog != null
    //if (dialog != null) {
    //  val parent = dialog.disposable
    //  val onParentDispose = Disposable {
    //    if (dialog.exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
    //      shouldFocusEditor = false
    //    }
    //  }
    //  if (dialog.isDisposed) {
    //    Disposer.dispose(onParentDispose)
    //  }
    //  else {
    //    Disposer.register(parent, onParentDispose)
    //  }
    //}
    //
    //// required invokeLater since in current call sequence KeyboardFocusManager is not initialized yet
    //// but future focused component
    //SwingUtilities.invokeLater { processFocusLost(e) }
  }

  private fun processFocusLost(e: FocusEvent) {
    emitModelChange()

    //val opposite = e.oppositeComponent
    //if (myPanel.isInFloatingMode && opposite != null && DialogWrapper.findInstance(opposite) != null) {
    //  myPanel.hideHint()
    //  return
    //}
    //val nodePopupInactive = !myPanel.isNodePopupActive
    //val childPopupInactive = !JBPopupFactory.getInstance().isChildPopupFocused(myPanel)
    //if (nodePopupInactive && childPopupInactive) {
    //  if (opposite != null && opposite !== myPanel && !myPanel.isAncestorOf(opposite) && !e.isTemporary) {
    //    myPanel.setContextComponent(null)
    //    myPanel.hideHint()
    //  }
    //}
    //myPanel.updateItems()
  }

  private fun emitPresentationChange() {
    check(myEventFlow.tryEmit(PresentationChangeEvent))
  }

  private fun emitModelChange() {
    check(myEventFlow.tryEmit(ModelChangeEvent))
  }

  override fun fileStatusesChanged() {
    emitPresentationChange()
  }

  override fun fileStatusChanged(virtualFile: VirtualFile) {
    emitPresentationChange()
  }

  override fun childAdded(event: PsiTreeChangeEvent) {
    emitModelChange()
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    emitModelChange()
  }

  override fun childMoved(event: PsiTreeChangeEvent) {
    emitModelChange()
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    emitModelChange()
  }

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    emitModelChange()
  }

  override fun rootsChanged(event: ModuleRootEvent) {
    emitModelChange()
  }

  override fun libraryRootsChanged(presentableLibraryName: @Nls String?,
                                   oldRoots: Collection<VirtualFile?>,
                                   newRoots: Collection<VirtualFile?>,
                                   libraryNameForDebug: String) {
    emitModelChange()
  }

  override fun problemsAppeared(file: VirtualFile) {
    emitModelChange()
  }

  override fun problemsDisappeared(file: VirtualFile) {
    emitModelChange()
  }

  override fun modelChanged() {
    emitPresentationChange()
  }

  override fun selectionChanged() {
    emitModelChange()
    //myPanel.updateItems()
    //myPanel.scrollSelectionToVisible()
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    val name = evt.propertyName
    if ("focusOwner" == name || "permanentFocusOwner" == name) {
      emitModelChange()
    }
  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    emitModelChange()

    //if (shouldSkipAction(action)) return
    //if (myPanel.isInFloatingMode) {
    //  myPanel.hideHint()
    //}
    //else {
    //  myPanel.cancelPopup()
    //}
  }

  override fun keyPressed(e: KeyEvent) {
    emitModelChange()

    //if (!(e.isAltDown || e.isMetaDown || e.isControlDown || myPanel.isNodePopupActive)) {
    //  if (!Character.isLetter(e.keyChar)) {
    //    return
    //  }
    //  myPanel.moveDown()
    //  SwingUtilities.invokeLater {
    //    try {
    //      val robot = Robot()
    //      val shiftOn = e.isShiftDown
    //      val code = e.keyCode
    //      if (shiftOn) {
    //        robot.keyPress(KeyEvent.VK_SHIFT)
    //      }
    //      robot.keyPress(code)
    //      robot.keyRelease(code)
    //    }
    //    catch (ignored: AWTException) {
    //    }
    //  }
    //}
  }

  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
    emitModelChange()

    //ApplicationManager.getApplication().invokeLater {
    //  if (myPanel.isFocused) {
    //    manager.openFile(file, true)
    //  }
    //}
  }

  override fun lookAndFeelChanged(source: LafManager) {
    //myPanel.navBarUI.clearItems()
    //myPanel.revalidate()
    //myPanel.repaint()
    emitPresentationChange()
  }

  override fun virtualFileAppearanceChanged(virtualFile: VirtualFile) {
    val psiFile = PsiManager.getInstance(myProject).findFile(virtualFile)
    if (psiFile != null) {
      //  myPanel.queueFileUpdate(psiFile)
      emitPresentationChange()
    }
  }

  //---- Ignored
  override fun windowLostFocus(e: WindowEvent) {}
  override fun windowGainedFocus(e: WindowEvent) {}
  override fun keyTyped(e: KeyEvent) {}
  override fun keyReleased(e: KeyEvent) {}
  override fun beforeChildAddition(event: PsiTreeChangeEvent) {}
  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}
  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}
  override fun beforeChildMovement(event: PsiTreeChangeEvent) {}
  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {}
  override fun beforePropertyChange(event: PsiTreeChangeEvent) {}
  override fun childRemoved(event: PsiTreeChangeEvent) {}

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    //myPanel.navBarUI.clearItems()
  }

  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    //myPanel.navBarUI.clearItems()
  }

  companion object {
    fun subscribeTo(project: Project) {

      //panel.addKeyListener(listener)
      //if (panel.isInFloatingMode) {
      //  val window = SwingUtilities.windowForComponent(panel)
      //  window?.addWindowFocusListener(listener)
      //}
      //else {
      //  ApplicationManager.getApplication().messageBus.connect(connection).subscribe(LafManagerListener.TOPIC, listener)
      //}
    }


    private fun shouldSkipAction(action: AnAction): Boolean {
      return (action is PopupAction
              || action is CopyAction
              || action is CutAction
              || action is ListActions
              || action is NavBarActions
              || action is ScrollingAction)
    }
  }
}