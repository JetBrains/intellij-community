package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextAccessor
import java.awt.Component
import java.io.File
import javax.swing.SwingUtilities

/**
 * Creates "browse" dialog for WSL
 * @param field field with wsl path
 */
class WslPathBrowser(private val field: TextAccessor) {

  fun browsePath(distro: WSLDistribution, parent: Component) {
    val virtualFile = ProgressManager.getInstance().runUnderProgress(IdeBundle.message("wsl.opening_wsl")) { getLocalPath(distro) }
    if (virtualFile == null) {
      JBPopupFactory.getInstance().createMessage(IdeBundle.message("wsl.no_path")).show(parent)
      return
    }
    val dialog = FileChooserDialogImpl(FileChooserDescriptorFactory.createAllButJarContentsDescriptor(), parent)
    val files = dialog.choose(null, virtualFile)
    val path = files.firstOrNull()?.let { distro.getWslPath(it.path) } ?: return
    field.text = path
  }

  private fun getLocalPath(distro: WSLDistribution): VirtualFile? {
    val fs = LocalFileSystem.getInstance()
    var file: VirtualFile? = null
    distro.getWindowsPath(field.text)?.let {
      var fileName = it
      while (file == null) {
        file = fs.findFileByPath(fileName)
        fileName = File(fileName).parent
      }
    }
    return file
  }
}

private fun <T> ProgressManager.runUnderProgress(@NlsContexts.DialogTitle title: String, code: () -> T): T =
  if (SwingUtilities.isEventDispatchThread()) {
    run(object : Task.WithResult<T, Exception>(null, title, false) {
      override fun compute(indicator: ProgressIndicator) = code()
    })
  }
  else {
    code()
  }
