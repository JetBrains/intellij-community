// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefCookieManager
import com.intellij.ui.jcef.JBCefRequestContext
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefRequestContext
import org.cef.callback.CefCompletionCallback
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

internal class RequestContextDemo : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout(0, 8))
  private val profileInfo = JLabel()
  private val profileSelector = JComboBox(PROFILES.toTypedArray())
  private val browsers = mutableListOf<BrowserEntry>()
  private val tabs = JBTabsFactory.createEditorTabs(null, this).apply {
    presentation.setTabDraggingEnabled(true)
    presentation.setEmptyText("Open a browser tab with the + action")
    addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        browsers.firstOrNull { it.tabInfo == newSelection }?.let {
          profileSelector.selectedItem = it.profile
        }
        updateProfileInfo()
      }
    })
  }
  private val newTabActions = createNewTabActions()
  private val logArea = JTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
    border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
  }

  init {
    profileSelector.addActionListener { updateProfileInfo() }
  }

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Request Contexts (Profiles)"

  override fun initializeImpl() {
    browsers.toList().forEach { Disposer.dispose(it.browser) }
    browsers.clear()
    tabs.removeAllTabs()
    myComponent.removeAll()

    val controls = JPanel(GridLayout(0, 1)).apply {
      add(JPanel(FlowLayout(FlowLayout.LEADING, 6, 2)).apply {
        add(profileSelector)
        add(JButton("New tab").apply {
          addActionListener { createBrowser(selectedProfile()) }
        })
        add(JButton("Remove profile").apply {
          addActionListener { removeProfile(selectedProfile()) }
        })
      })
      add(JPanel(FlowLayout(FlowLayout.LEADING, 6, 2)).apply {
        add(JButton("List cookies").apply {
          addActionListener { listCookies(selectedProfile()) }
        })
        add(JButton("Delete cookies").apply {
          addActionListener { deleteCookies(selectedProfile()) }
        })
        add(JButton("Clear HTTP cache").apply {
          addActionListener { clearHttpCache(selectedProfile()) }
        })
      })
      add(profileInfo)
    }

    myComponent.add(controls, BorderLayout.NORTH)
    myComponent.add(tabs.component, BorderLayout.CENTER)
    myComponent.add(JBScrollPane(logArea).apply { preferredSize = Dimension(0, 125) }, BorderLayout.SOUTH)

    log("Compare the two in-memory profiles: open a page in one, save a note, then load the sibling browser.")
    log("Browsers in the same profile share cookies and localStorage; other profiles remain isolated.")
    log("The persistent profile keeps its on-disk data after removal. The Global option uses the normal JCEF profile.")
    updateProfileInfo()
    createBrowser(IN_MEMORY_A)
    createBrowser(IN_MEMORY_B)
  }

  private fun createBrowser(profile: ProfileOption) {
    val requestContext = try {
      requestContextFor(profile)
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot get profile: ${e.message}")
      return
    }

    val browser = try {
      JBCefBrowserBuilder()
        .setRequestContext(requestContext)
        .setEnableOpenDevToolsMenuItem(true)
        .build()
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot create browser: ${e.message}")
      return
    }
    Disposer.register(this, browser)

    val localRequestHandler = JBCefLocalRequestHandler("https", "jcef-profile-demo")
    val pageUrl = localRequestHandler.createResource("index.html") {
      javaClass.getResourceAsStream("resources/request_context/index.html")?.let {
        JBCefStreamResourceHandler(it, "text/html", this)
      }
    } + "?profile=" + URLEncoder.encode(profile.label, StandardCharsets.UTF_8)
    browser.jbCefClient.addRequestHandler(localRequestHandler, browser.cefBrowser)

    val addressField = JTextField(pageUrl).apply {
      preferredSize = Dimension(300, preferredSize.height)
      addActionListener { loadAddress(browser, this) }
    }
    val content = JPanel(BorderLayout(0, 4))
    val navigation = JPanel(BorderLayout(4, 0)).apply {
      add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 2)).apply {
        add(JButton("Back").apply { addActionListener { browser.cefBrowser.goBack() } })
        add(JButton("Forward").apply { addActionListener { browser.cefBrowser.goForward() } })
        add(JButton("Reload").apply { addActionListener { browser.cefBrowser.reload() } })
      }, BorderLayout.WEST)
      add(addressField, BorderLayout.CENTER)
      add(JButton("DevTools").apply {
        addActionListener { browser.cefBrowser.openDevTools() }
      }, BorderLayout.EAST)
    }
    content.add(navigation, BorderLayout.NORTH)
    content.add(browser.component, BorderLayout.CENTER)
    val tabInfo = TabInfo(content)
      .setText(profile.tabLabel)
      .setTooltipText(HtmlChunk.text(profile.label))
      .setTabPaneActions(newTabActions)
    val closeActions = DefaultActionGroup().apply { add(createCloseTabAction(profile, tabInfo)) }
    tabInfo.setTabLabelActions(closeActions, ActionPlaces.EDITOR_TAB)
    val entry = BrowserEntry(profile, browser, tabInfo, addressField)
    browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
      override fun onTitleChange(cefBrowser: org.cef.browser.CefBrowser, title: String) {
        SwingUtilities.invokeLater {
          if (browsers.contains(entry)) tabInfo.setText(tabText(profile, title))
        }
      }

      override fun onAddressChange(cefBrowser: org.cef.browser.CefBrowser, frame: org.cef.browser.CefFrame, url: String) {
        if (!frame.isMain) return
        SwingUtilities.invokeLater {
          if (browsers.contains(entry)) addressField.text = url
        }
      }
    }, browser.cefBrowser)
    browsers += entry
    tabs.addTab(tabInfo)
    tabs.select(tabInfo, true)
    browser.loadURL(pageUrl)
    log("Created browser in ${profile.label}.")
  }

  private fun loadAddress(browser: JBCefBrowser, addressField: JTextField) {
    val url = addressField.text.trim()
    if (url.isNotEmpty()) browser.loadURL(url)
  }

  private fun createNewTabActions(): DefaultActionGroup = DefaultActionGroup("New tab", true).apply {
    templatePresentation.icon = AllIcons.General.Add
    templatePresentation.description = "Open a browser in a profile"
    PROFILES.forEach { profile ->
      add(object : DumbAwareAction(profile.label) {
        override fun actionPerformed(e: AnActionEvent) = createBrowser(profile)

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      })
    }
  }

  private fun createCloseTabAction(profile: ProfileOption, tabInfo: TabInfo): DumbAwareAction = object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      browsers.firstOrNull { it.tabInfo == tabInfo }?.let(::closeBrowser)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = AllIcons.Actions.Close
      e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
      e.presentation.text = "Close ${profile.tabLabel}"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private fun tabText(profile: ProfileOption, title: String): String {
    val pageTitle = title.trim().takeIf { it.isNotEmpty() }?.take(28) ?: return profile.tabLabel
    return "${profile.tabLabel} — $pageTitle"
  }

  private fun listCookies(profile: ProfileOption) {
    val cookieManager = try {
      cookieManagerFor(profile)
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot get cookie manager: ${e.message}")
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val cookies = cookieManager.getCookies(null, true).get(10, TimeUnit.SECONDS)
        log("[${profile.label}] ${cookies.size} cookie(s): ${cookies.joinToString()}")
      }
      catch (e: Exception) {
        log("[${profile.label}] Cookie listing failed: ${e.message}")
      }
    }
  }

  private fun deleteCookies(profile: ProfileOption) {
    val cookieManager = try {
      cookieManagerFor(profile)
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot get cookie manager: ${e.message}")
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val deleted = cookieManager.deleteCookies("", "").get(10, TimeUnit.SECONDS)
        log("[${profile.label}] Cookie deletion completed: $deleted")
      }
      catch (e: Exception) {
        log("[${profile.label}] Cookie deletion failed: ${e.message}")
      }
    }
  }

  private fun clearHttpCache(profile: ProfileOption) {
    try {
      val context = requestContextFor(profile)
      if (context == null) {
        CefRequestContext.getGlobalContext().clearHttpCache(object : CefCompletionCallback {
          override fun onComplete() = log("[${profile.label}] HTTP cache cleared.")
        })
      }
      else {
        context.clearHttpCache().whenComplete { _, error ->
          if (error == null) log("[${profile.label}] HTTP cache cleared.")
          else log("[${profile.label}] HTTP cache clearing failed: ${error.message}")
        }
      }
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot clear HTTP cache: ${e.message}")
    }
  }

  private fun removeProfile(profile: ProfileOption) {
    try {
      if (profile.isGlobal) {
        log("The Global profile cannot be removed by this demo.")
        return
      }
      browsers.filter { it.profile == profile }.toList().forEach(::closeBrowser)
      val context = requestContextFor(profile) ?: return
      JBCefApp.getInstance().disposeRequestContext(context)
      profile.context = null
      profileInfo.text = "Removed. Add a browser to create a new context."
      log("Removed ${profile.label}; persistent on-disk data is not deleted.")
    }
    catch (e: RuntimeException) {
      log("[${profile.label}] Cannot remove profile: ${e.message}")
    }
  }

  private fun closeBrowser(entry: BrowserEntry) {
    if (!browsers.remove(entry)) return
    tabs.removeTab(entry.tabInfo)
    Disposer.dispose(entry.browser)
    log("Closed browser in ${entry.profile.label}.")
  }

  private fun cookieManagerFor(profile: ProfileOption): JBCefCookieManager =
    requestContextFor(profile)?.getCookieManager() ?: JBCefBrowserBase.getGlobalJBCefCookieManager()

  private fun requestContextFor(profile: ProfileOption): JBCefRequestContext? {
    val app = JBCefApp.getInstance()
    return when {
      profile.memoryLabel != null -> {
        profile.context?.takeUnless { it.isDisposed() }
          ?: app.createRequestContext().also { profile.context = it }
      }
      profile.cachePath != null -> app.getRequestContext(profile.cachePath)
      else -> null
    }
  }

  private fun selectedProfile(): ProfileOption = profileSelector.selectedItem as? ProfileOption ?: IN_MEMORY_A

  private fun updateProfileInfo() {
    val profile = selectedProfile()
    if (profile.isGlobal) {
      profileInfo.text = "Global profile (normal browser data)"
      return
    }
    try {
      val context = requestContextFor(profile)
      profileInfo.text = "${context?.getCachePath() ?: "in-memory"} | disposed=${context?.isDisposed()}"
    }
    catch (e: RuntimeException) {
      profileInfo.text = "Unavailable: ${e.message}"
    }
  }

  private fun log(message: String) {
    SwingUtilities.invokeLater {
      if (logArea.text.isEmpty()) logArea.text = message else logArea.append("\n$message")
      logArea.caretPosition = logArea.document.length
    }
  }

  private data class ProfileOption(
    val label: String,
    val memoryLabel: String? = null,
    val cachePath: Path? = null,
    val isGlobal: Boolean = false,
  ) {
    var context: JBCefRequestContext? = null

    val tabLabel: String
      get() = when {
        isGlobal -> "Global"
        memoryLabel != null -> memoryLabel
        else -> "Persistent"
      }

    override fun toString(): String = label
  }

  private data class BrowserEntry(
    val profile: ProfileOption,
    val browser: JBCefBrowser,
    val tabInfo: TabInfo,
    val addressField: JTextField,
  )

  companion object {
    private val GLOBAL = ProfileOption("Global (normal browser profile)", isGlobal = true)
    private val IN_MEMORY_A = ProfileOption("In-memory: profile-a", memoryLabel = "profile-a")
    private val IN_MEMORY_B = ProfileOption("In-memory: profile-b", memoryLabel = "profile-b")
    private val PERSISTENT = ProfileOption("Persistent: jcef-test-app-profile", cachePath = Path.of("jcef-test-app-profile"))
    private val PROFILES = listOf(GLOBAL, IN_MEMORY_A, IN_MEMORY_B, PERSISTENT)
  }
}