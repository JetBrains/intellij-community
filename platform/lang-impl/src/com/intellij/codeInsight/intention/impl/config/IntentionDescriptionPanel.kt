// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.actions.ShowSettingsUtilImpl.Companion.showSettingsDialog
import com.intellij.ide.ui.search.SearchUtil.markup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SettingsUtil
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

@ApiStatus.Internal
class IntentionDescriptionPanel {
  val component: JPanel

  private val myAfterPanel = JPanel()
  private val myBeforePanel = JPanel()
  private val myDescriptionBrowser = DescriptionEditorPane()
  private val myBeforeUsagePanels: MutableList<ActionUsagePanel> = ArrayList()
  private val myAfterUsagePanels: MutableList<ActionUsagePanel> = ArrayList()
  private lateinit var myBeforeWrapperRow: Row
  private lateinit var myAfterWrapperRow: Row

  init {
    val descriptionScrollPane = ScrollPaneFactory.createScrollPane(myDescriptionBrowser)
    descriptionScrollPane.border = JBUI.Borders.empty()

    val examplePanel = JBSplitter(true)
    examplePanel.setResizeEnabled(false)
    val gap = VerticalComponentGap(top = true, bottom = false)
    examplePanel.firstComponent = panel {
      myBeforeWrapperRow = row {
        cell(myBeforePanel)
          .label(CodeInsightBundle.message("border.title.before"), LabelPosition.TOP)
          .align(Align.FILL)
          .applyToComponent {
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, gap)
          }
      }.topGap(TopGap.SMALL).resizableRow()
    }
    examplePanel.secondComponent = panel {
      myAfterWrapperRow = row {
        cell(myAfterPanel)
          .label(CodeInsightBundle.message("border.title.after"), LabelPosition.TOP)
          .align(Align.FILL)
          .applyToComponent {
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, gap)
          }
      }.resizableRow()
    }
    examplePanel.firstComponent.minimumSize = JBUI.size(100)
    examplePanel.secondComponent.minimumSize = JBUI.size(100)

    val mySplitter = OnePixelSplitter(true, "IntentionDescriptionPanel.VERTICAL_DIVIDER_PROPORTION", DIVIDER_PROPORTION_DEFAULT)
    mySplitter.setFirstComponent(descriptionScrollPane)
    mySplitter.setSecondComponent(examplePanel)
    mySplitter.dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    this.component = mySplitter

    myDescriptionBrowser.addHyperlinkListener(HyperlinkListener { e: HyperlinkEvent? ->
      if (e!!.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        try {
          val url = URI(e.description)
          if (url.scheme == "settings") {
            DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(Consumer { context: DataContext? ->
              if (context != null) {
                val settings = Settings.KEY.getData(context)
                val searchTextField = SearchTextField.KEY.getData(context)
                val configId = url.host
                val search = url.query
                if (settings != null) {
                  val configurable = settings.find(configId)
                  settings.select(configurable).doWhenDone(Runnable {
                    if (searchTextField != null && search != null) searchTextField.text = search
                  })
                }
                else {
                  val project = context.getData(CommonDataKeys.PROJECT)
                  showSettingsDialog(project, configId, search)
                }
              }
            })
          }
          else {
            BrowserUtil.browse(url)
          }
        }
        catch (ex: URISyntaxException) {
          LOG.error(ex)
        }
      }
    })
  }

  fun reset(actionMetaData: IntentionActionMetaData, filter: String?) {
    try {
      val url = actionMetaData.getDescription()
      val description = if (StringUtil.isEmpty(url.getText())) CodeInsightBundle.message("under.construction.string")
      else markup(SettingsUtil.wrapWithPoweredByMessage(url.getText(), actionMetaData.loader), filter)

      myDescriptionBrowser.readHTML(description)

      myBeforeWrapperRow.visible(!actionMetaData.isSkipBeforeAfter)
      myAfterWrapperRow.visible(!actionMetaData.isSkipBeforeAfter)
      showUsages(myBeforePanel, myBeforeUsagePanels, actionMetaData.getExampleUsagesBefore())
      showUsages(myAfterPanel, myAfterUsagePanels, actionMetaData.getExampleUsagesAfter())
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  fun reset(intentionCategory: String?) {
    if (intentionCategory == null) {
      myDescriptionBrowser.text = ""
      myBeforeWrapperRow.visible(false)
      myAfterWrapperRow.visible(false)
    }
    else try {
      myDescriptionBrowser.readHTML(CodeInsightBundle.message("intention.settings.category.text", intentionCategory))

      val beforeTemplate =
        PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.before"), "before.java.template")
      val afterTemplate =
        PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.after"), "after.java.template")
      myBeforeWrapperRow.visible(true)
      myAfterWrapperRow.visible(true)
      showUsages(myBeforePanel, myBeforeUsagePanels, arrayOf(beforeTemplate))
      showUsages(myAfterPanel, myAfterUsagePanels, arrayOf(afterTemplate))
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  fun dispose() {
    disposeUsagePanels(myBeforeUsagePanels)
    disposeUsagePanels(myAfterUsagePanels)
  }

  companion object {
    private val LOG = Logger.getInstance(IntentionDescriptionPanel::class.java)
    private const val DIVIDER_PROPORTION_DEFAULT = .25f

    @Throws(IOException::class)
    private fun showUsages(
      panel: JPanel,
      usagePanels: MutableList<ActionUsagePanel>,
      exampleUsages: Array<TextDescriptor>?
    ) {
      var gb: GridBagConstraints? = null
      val reuse = exampleUsages != null && panel.components.size == exampleUsages.size
      if (!reuse) {
        disposeUsagePanels(usagePanels)
        panel.layout = GridBagLayout()
        panel.removeAll()
        gb = GridBagConstraints()
        gb.anchor = GridBagConstraints.NORTHWEST
        gb.fill = GridBagConstraints.BOTH
        gb.gridheight = GridBagConstraints.REMAINDER
        gb.gridwidth = 1
        gb.gridx = 0
        gb.gridy = 0
        gb.insets = JBUI.emptyInsets()
        gb.ipadx = 5
        gb.ipady = 5
        gb.weightx = 1.0
        gb.weighty = 1.0
      }

      if (exampleUsages != null) {
        for (i in exampleUsages.indices) {
          val exampleUsage = exampleUsages[i]
          val name = exampleUsage.getFileName()
          val fileTypeManager = FileTypeManagerEx.getInstanceEx()
          val extension = fileTypeManager.getExtension(name)
          val fileType = fileTypeManager.getFileTypeByExtension(extension)

          val usagePanel: ActionUsagePanel
          if (reuse) {
            usagePanel = panel.getComponent(i) as ActionUsagePanel
          }
          else {
            usagePanel = ActionUsagePanel()
            usagePanels.add(usagePanel)
          }
          usagePanel.reset(exampleUsage.getText(), fileType)

          if (!reuse) {
            panel.add(usagePanel, gb)
            gb!!.gridx++
          }
        }
      }
      panel.revalidate()
      panel.repaint()
    }

    private fun disposeUsagePanels(usagePanels: MutableList<out ActionUsagePanel>) {
      for (usagePanel in usagePanels) {
        Disposer.dispose(usagePanel)
      }
      usagePanels.clear()
    }
  }
}