// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class ContextMenu : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Context Menu"

  override fun initializeImpl() {
    myBrowser = JBCefBrowserBuilder().setUrl("https://duckduckgo.com/").build()
    Disposer.register(this, myBrowser!!)
    enableBasicContextMenu(myEnableBasicContextMenuCheckbox.isSelected)
    enableCustomContextMenu(myEnableCustomMenuCheckbox.isSelected)

    myComponent.removeAll()
    myComponent.add(myControlPanel, BorderLayout.SOUTH)
    myComponent.add(myBrowser!!.component!!, BorderLayout.CENTER)
  }

  private fun enableBasicContextMenu(value: Boolean) {
    myBrowser!!.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, !value)
  }

  private fun enableCustomContextMenu(value: Boolean) {
    val browser = myBrowser!!
    if (value) {
      browser.jbCefClient.addContextMenuHandler(myCustomMenuHandler, browser.cefBrowser)
    } else {
      browser.jbCefClient.removeContextMenuHandler(myCustomMenuHandler, browser.cefBrowser)
    }
  }

  private val myEnableBasicContextMenuCheckbox = JCheckBox("Enable default context menu").apply {
    isSelected = true
    addActionListener { enableBasicContextMenu(isSelected) }
  }
  private val myEnableCustomMenuCheckbox = JCheckBox("Enable custom context menu").apply {
    isSelected = true
    addActionListener { enableCustomContextMenu(isSelected) }
  }
  private val myControlPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    add(myEnableBasicContextMenuCheckbox)
    add(myEnableCustomMenuCheckbox)
  }
  private val myComponent = JPanel(BorderLayout())
  private var myBrowser: JBCefBrowserBase? = null

  private val myCustomMenuHandler = object : CefContextMenuHandlerAdapter() {

    private val SAY_HALLO_ID = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 10

    private val CHECK_ITEMS_SUBMENU = SAY_HALLO_ID + 1

    private val CHECK_ITEM_1 = CHECK_ITEMS_SUBMENU + 1
    private val CHECK_ITEM_2 = CHECK_ITEM_1 + 1
    private val CHECK_ITEM_3 = CHECK_ITEM_2 + 1
    private var mySelectedItem = CHECK_ITEM_1

    private val RADIO_ITEMS_SUBMENU = CHECK_ITEM_3 + 1
    private val RADIO_ITEM_1 = RADIO_ITEMS_SUBMENU + 1
    private val RADIO_ITEM_2 = RADIO_ITEM_1 + 1
    private val RADIO_ITEM_3 = RADIO_ITEM_2 + 1
    private var mySelectedRadioItem = RADIO_ITEM_1

    private var INACTIVE_ITEMS_SUBMENU = RADIO_ITEM_3 + 1
    private val INACTIVE_ITEM_1 = INACTIVE_ITEMS_SUBMENU + 1
    private val INACTIVE_ITEM_2 = INACTIVE_ITEM_1 + 1

    override fun onBeforeContextMenu(browser: CefBrowser, frame: CefFrame, params: CefContextMenuParams, model: CefMenuModel) {
      if (model.count > 0) {
        model.addSeparator()
      }
      model.addItem(SAY_HALLO_ID, "Say hello")

      model.addSubMenu(CHECK_ITEMS_SUBMENU, "Check items").apply {
        addCheckItem(CHECK_ITEM_1, "Item 1")
        addCheckItem(CHECK_ITEM_2, "Item 2")
        addCheckItem(CHECK_ITEM_3, "Item 3")
        setChecked(mySelectedItem, true)
      }

      model.addSubMenu(RADIO_ITEMS_SUBMENU, "Radio items").apply {
        addRadioItem(RADIO_ITEM_1, "Item 1", 1)
        addRadioItem(RADIO_ITEM_2, "Item 2", 1)
        addRadioItem(RADIO_ITEM_3, "Item 3", 1)
        setChecked(mySelectedRadioItem, true)
      }

      model.addSubMenu(INACTIVE_ITEMS_SUBMENU, "Inactive items").apply {
        addItem(INACTIVE_ITEM_1, "Disabled")
        setEnabled(INACTIVE_ITEM_1, false)
        addItem(INACTIVE_ITEM_2, "Invisible")
        setVisible(INACTIVE_ITEM_2, false)
      }
    }

    override fun onContextMenuCommand(browser: CefBrowser, frame: CefFrame, params: CefContextMenuParams, commandId: Int, eventFlags: Int): Boolean {
      when (commandId) {
        SAY_HALLO_ID -> {
          SwingUtilities.invokeLater {
            Messages.showMessageDialog ("Say hallo menu item was selected", "Hello", Messages.getInformationIcon())
          }
          return true
        }

        CHECK_ITEM_1, CHECK_ITEM_2, CHECK_ITEM_3 -> {
          mySelectedItem = commandId
          return true
        }

        RADIO_ITEM_1, RADIO_ITEM_2, RADIO_ITEM_3 -> {
          mySelectedRadioItem = commandId
          return true
        }
      }
      return super.onContextMenuCommand(browser, frame, params, commandId, eventFlags)
    }
  }
}