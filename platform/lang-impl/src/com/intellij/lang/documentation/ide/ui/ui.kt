// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.documentation.CornerAwareScrollPaneLayout
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollPane

internal val LOG: Logger = Logger.getInstance("#com.intellij.lang.documentation.ide.ui")

/**
 * @see com.intellij.find.actions.ShowUsagesAction.ourPopupDelayTimeout
 */
internal const val DEFAULT_UI_RESPONSE_TIMEOUT: Long = 300

@JvmField
internal val FORCED_WIDTH = Key.create<Int>("WidthBasedLayout.width")

internal typealias UISnapshot = () -> Unit

internal fun toolbarComponent(actions: ActionGroup, contextComponent: JComponent): JComponent {
  val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true).also {
    it.setSecondaryActionsIcon(AllIcons.Actions.More, true)
    it.setTargetComponent(contextComponent)
  }
  return toolbar.component.also {
    it.border = IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.BOTTOM)
  }
}

internal fun actionButton(actions: ActionGroup, contextComponent: JComponent): JComponent {
  val presentation = Presentation().also {
    it.icon = AllIcons.Actions.More
    it.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }
  val button = object : ActionButton(actions, presentation, ActionPlaces.UNKNOWN, Dimension(20, 20)) {
    override fun getDataContext(): DataContext = DataManager.getInstance().getDataContext(contextComponent)
  }
  button.setNoIconsInPopup(true)
  return button
}

internal fun scrollPaneWithCorner(parent: Disposable, scrollPane: JScrollPane, corner: JComponent): JComponent {
  val defaultLayout = scrollPane.layout
  scrollPane.layout = CornerAwareScrollPaneLayout(corner)
  Disposer.register(parent) {
    scrollPane.layout = defaultLayout
  }
  val layeredPane: JLayeredPane = object : JBLayeredPane() {

    override fun doLayout() {
      val r = bounds
      for (component in components) {
        if (component === scrollPane) {
          component.setBounds(0, 0, r.width, r.height)
        }
        else if (component === corner) {
          val d = component.preferredSize
          component.setBounds(r.width - d.width - 2, r.height - d.height - 2, d.width, d.height)
        }
        else {
          error("can't layout unexpected component: $component")
        }
      }
    }

    override fun getPreferredSize(): Dimension {
      return scrollPane.preferredSize
    }
  }
  layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
  layeredPane.add(scrollPane)
  layeredPane.setLayer(corner, JLayeredPane.PALETTE_LAYER)
  layeredPane.add(corner)
  return layeredPane
}

@Suppress("TestOnlyProblems")
internal fun linkChunk(presentableText: @Nls String, data: DocumentationData): HtmlChunk? {
  val externalUrl = data.externalUrl
  if (externalUrl != null) {
    return DocumentationManager.getLink(presentableText, externalUrl)
           ?: DocumentationManager.getGenericExternalDocumentationLink(presentableText)
  }
  val linkUrls = data.linkUrls
  if (linkUrls.isNotEmpty()) {
    return DocumentationManager.getExternalLinks(presentableText, linkUrls)
           ?: DocumentationManager.getGenericExternalDocumentationLink(presentableText)
  }
  return null
}
