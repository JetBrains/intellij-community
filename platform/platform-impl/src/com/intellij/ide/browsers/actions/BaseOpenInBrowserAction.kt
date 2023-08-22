// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.*
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.BitUtil
import com.intellij.util.Url
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.event.ActionEvent

internal class BaseOpenInBrowserAction(private val browser: WebBrowser) : DumbAwareAction(browser.name, null, browser.icon) {
  object Handler {
    private val LOG = logger<BaseOpenInBrowserAction>()

    @JvmStatic
    fun doUpdate(event: AnActionEvent): OpenInBrowserRequest? {
      val request = createRequest(event.dataContext, isForceFileUrlIfNoUrlProvider = false)
      val applicable = request != null && WebBrowserServiceImpl.getProviders(request).any()
      event.presentation.isEnabledAndVisible = applicable
      return if (applicable) request else  null
    }

    internal fun openInBrowser(request: OpenInBrowserRequest, preferLocalUrl: Boolean = false, browser: WebBrowser? = null) {
      try {
        val urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl)
        if (!urls.isEmpty()) {
          chooseUrl(urls)
            .onSuccess { url ->
              FileDocumentManager.getInstance().saveAllDocuments()
              BrowserLauncher.instance.browse(url.toExternalForm(), browser, request.project)
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

    internal fun openInBrowser(event: AnActionEvent, browser: WebBrowser?) {
      createRequest(event.dataContext, isForceFileUrlIfNoUrlProvider = true)?.let {
        openInBrowser(it, BitUtil.isSet(event.modifiers, ActionEvent.SHIFT_MASK), browser)
      }
    }
  }

  private fun getBrowser(): WebBrowser? =
    if (WebBrowserManager.getInstance().isActive(browser) && browser.path != null) browser else null

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
      result!!
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
      val psiFile = CommonDataKeys.PSI_FILE.getData(context) ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
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

internal fun chooseUrl(urls: Collection<Url>): Promise<Url> {
  if (urls.size == 1) {
    return resolvedPromise(urls.first())
  }

  val result = AsyncPromise<Url>()
  JBPopupFactory.getInstance()
    .createPopupChooserBuilder(urls.toMutableList())
    .setRenderer(SimpleListCellRenderer.create { label, value, _ ->
      // todo icons looks good, but is it really suitable for all URLs providers?
      label.icon = AllIcons.Nodes.Servlet
      label.text = (value as Url).toDecodedForm()
    })
    .setTitle(IdeBundle.message("browser.url.popup"))
    .setItemChosenCallback { value ->
      result.setResult(value)
    }
    .createPopup()
    .showInFocusCenter()
  return result
}
