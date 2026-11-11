// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.EditorTabWebBrowser
import com.intellij.ide.browsers.EditorTabWebBrowserProvider
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.SystemDefaultBrowser
import com.intellij.ide.browsers.SystemDefaultBrowserDetector
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.ide.browsers.WebBrowserUrlProvider
import com.intellij.ide.browsers.WebBrowserXmlService
import com.intellij.ide.browsers.actions.BaseOpenInBrowserAction.Handler.openInRightSplit
import com.intellij.ide.browsers.createOpenInBrowserRequest
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.BitUtil
import com.intellij.util.Url
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.event.ActionEvent

open class BaseOpenInBrowserAction(private val browser: WebBrowser) : DumbAwareAction({ browser.name }, null, { browser.icon }) {
  object Handler {
    private val LOG = logger<BaseOpenInBrowserAction>()

    @JvmStatic
    fun doUpdate(event: AnActionEvent): OpenInBrowserRequest? {
      val request = createRequest(event.dataContext, isForceFileUrlIfNoUrlProvider = false)
      val applicable = request != null && WebBrowserServiceImpl.getProviders(request).any()
      event.presentation.isEnabledAndVisible = applicable
      return if (applicable) request else  null
    }

    /**
     * Opens the URL of [request] in [browser].
     * An [EditorTabWebBrowser] opens it in a new tab in the right split of the editor, see [openInRightSplit].
     */
    internal fun openInBrowser(request: OpenInBrowserRequest, preferLocalUrl: Boolean = false, browser: WebBrowser? = null) {
      try {
        val urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl)
        if (!urls.isEmpty()) {
          chooseUrl(urls)
            .onSuccess { url ->
              FileDocumentManager.getInstance().saveAllDocuments()
              ManagingFS.getInstance().flushPendingUpdatesOrNotify()
              val externalUrl = url.toExternalForm()
              if (browser !is EditorTabWebBrowser || !openInRightSplit(request.project, browser, externalUrl)) {
                BrowserLauncher.instance.browse(externalUrl, browser, request.project)
              }
            }
        }
      }
      catch (e: WebBrowserUrlProvider.BrowserException) {
        Messages.showErrorDialog(e.message, IdeBundle.message("browser.error"))
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }

    /**
     * Opens [url] in a new tab of [browser] in the right split of the editor, as the built-in preview does.
     *
     * @return `false` when no tab opened, for example when the editor has no current window
     */
    private fun openInRightSplit(project: Project, browser: EditorTabWebBrowser, url: String): Boolean {
      val file = browser.createEditorTabFile(project, url)
      return FileEditorManagerEx.getInstanceEx(project).splitters.openInRightSplit(file, requestFocus = false) != null
    }

    internal fun openInBrowser(event: AnActionEvent, browser: WebBrowser?) {
      createRequest(event.dataContext, isForceFileUrlIfNoUrlProvider = true)?.let {
        openInBrowser(it, BitUtil.isSet(event.modifiers, ActionEvent.SHIFT_MASK), browser)
      }
    }
  }

  /**
   * Returns [browser] while it can open a page.
   * A browser of the list needs a path. [SystemDefaultBrowser] has no path.
   * An [EditorTabWebBrowser] has no path and must be available.
   */
  private fun getBrowser(): WebBrowser? {
    val canOpen = browser.path != null ||
                  browser === SystemDefaultBrowser ||
                  EditorTabWebBrowserProvider.getAvailableBrowsers().any { it === browser }
    return if (canOpen && WebBrowserManager.getInstance().isActive(browser)) browser else null
  }

  override fun update(e: AnActionEvent) {
    val browser = getBrowser()
    if (browser == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val result: OpenInBrowserRequest?
    if (e.place == ActionPlaces.UNKNOWN) {
      result = createRequest(e.dataContext, isForceFileUrlIfNoUrlProvider = true)
      val isApplicable = result?.isPhysicalFile() ?: false
      e.presentation.isEnabledAndVisible = isApplicable
      if (!isApplicable) {
        return
      }
    }
    else {
      result = Handler.doUpdate(e) ?: return
    }

    var description = templatePresentation.text
    if (ActionPlaces.CONTEXT_TOOLBAR == e.place) {
      val shortcut = KeymapUtil.getPrimaryShortcut("WebOpenInAction")
      val htmlFile = WebBrowserXmlService.getInstance().isHtmlFile(result.file)
      val shortcutInfo = when {
        shortcut != null && htmlFile -> IdeBundle.message("browser.shortcut.or.shift", KeymapUtil.getShortcutText(shortcut))
        shortcut != null && !htmlFile -> KeymapUtil.getShortcutText(shortcut)
        shortcut == null && htmlFile -> IdeBundle.message("browser.shortcut")
        else -> ""
      }
      if (shortcutInfo.isNotEmpty()) {
        description = "$description ($shortcutInfo)"
      }
    }
    e.presentation.text = description
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    getBrowser()?.let {
      Handler.openInBrowser(e, it)
    }
  }
}

/**
 * Opens the file in [SystemDefaultBrowser].
 * It is a separate class, so the action statistics tell it apart from a browser of the list.
 * It shows the icon of the browser that the OS opens, see [SystemDefaultBrowserDetector].
 */
internal class OpenInSystemDefaultBrowserAction : BaseOpenInBrowserAction(SystemDefaultBrowser) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isVisible) {
      val knownBrowser = SystemDefaultBrowserDetector.getInstance().knownBrowser
      e.presentation.icon = knownBrowser?.let(SystemDefaultBrowserDetector::iconOf) ?: SystemDefaultBrowser.icon
    }
  }
}

/**
 * Opens the file in an [EditorTabWebBrowser], such as the IDE Browser.
 * It is a separate class, so the action statistics tell it apart from a browser of the list.
 */
@ApiStatus.Internal
class OpenInProvidedBrowserAction(browser: EditorTabWebBrowser) : BaseOpenInBrowserAction(browser)

private fun createRequest(context: DataContext, isForceFileUrlIfNoUrlProvider: Boolean): OpenInBrowserRequest? {
  val editor = CommonDataKeys.EDITOR.getData(context)
  if (editor == null) {
    var psiFile = CommonDataKeys.PSI_FILE.getData(context)
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context)
    val project = CommonDataKeys.PROJECT.getData(context)
    if (virtualFile != null && !virtualFile.isDirectory && virtualFile.isValid && project != null && project.isInitialized) {
      psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    }

    if (psiFile != null) {
      return createOpenInBrowserRequest(psiFile, isForceFileUrlIfNoUrlProvider)
    }
  }
  else {
    val project = editor.project
    if (project != null && project.isInitialized) {
      val psiFile = CommonDataKeys.PSI_FILE.getData(context) ?: EditorContextManager.getPsiFileForEditor(editor, project)
      if (psiFile != null) {
        return object : OpenInBrowserRequest(psiFile, isForceFileUrlIfNoUrlProvider) {
          private val lazyElement by lazy { file.findElementAt(editor.caretModel.offset) }

          override val element: PsiElement
            get() = lazyElement ?: file
        }
      }
    }
  }
  return null
}

fun chooseUrl(urls: Collection<Url>): Promise<Url> {
  if (urls.size == 1) {
    return resolvedPromise(urls.first())
  }

  val result = AsyncPromise<Url>()
  JBPopupFactory.getInstance()
    .createPopupChooserBuilder(urls.toMutableList())
    .setRenderer(listCellRenderer("") {
      // todo icons looks good, but is it really suitable for all URLs providers?
      icon(AllIcons.Nodes.Servlet)
      text(value.toDecodedForm())
    })
    .setTitle(IdeBundle.message("browser.url.popup"))
    .setItemChosenCallback { value ->
      result.setResult(value)
    }
    .createPopup()
    .showInFocusCenter()
  return result
}
