// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SettingsUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

@ApiStatus.Internal
class IntentionDescriptionPanel {
  val component: JPanel

  private val myAfterPanel: JPanel
  private val myBeforePanel: JPanel
  private val myDescriptionBrowser = DescriptionEditorPane()
  private val myBeforeUsagePanels: MutableList<IntentionUsagePanel> = ArrayList<IntentionUsagePanel>()
  private val myAfterUsagePanels: MutableList<IntentionUsagePanel> = ArrayList<IntentionUsagePanel>()
  private val myBeforeWrapperPanel: JPanel
  private val myAfterWrapperPanel: JPanel

  init {
    val descriptionScrollPane = ScrollPaneFactory.createScrollPane(myDescriptionBrowser)
    descriptionScrollPane.setBorder(null)

    val examplePanel = JPanel(GridBagLayout())
    val constraint = GridBag()
      .setDefaultInsets(UIUtil.LARGE_VGAP, 0, 0, 0)
      .setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultWeightY(0.5)
      .setDefaultWeightX(1.0)

    myBeforePanel = JPanel()
    myBeforeWrapperPanel = UI.PanelFactory.panel(myBeforePanel)
      .withLabel(CodeInsightBundle.message("border.title.before"))
      .moveLabelOnTop()
      .resizeX(true)
      .resizeY(true)
      .createPanel()
    examplePanel.add(myBeforeWrapperPanel, constraint.nextLine())

    myAfterPanel = JPanel()
    myAfterWrapperPanel = UI.PanelFactory.panel(myAfterPanel)
      .withLabel(CodeInsightBundle.message("border.title.after"))
      .moveLabelOnTop()
      .resizeX(true)
      .resizeY(true)
      .createPanel()
    examplePanel.add(myAfterWrapperPanel, constraint.nextLine()
    )

    val mySplitter = OnePixelSplitter(true,
                                      "IntentionDescriptionPanel.VERTICAL_DIVIDER_PROPORTION",
                                      DIVIDER_PROPORTION_DEFAULT)
    mySplitter.setFirstComponent(descriptionScrollPane)
    mySplitter.setSecondComponent(examplePanel)
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
                val search = url.getQuery()
                if (settings != null) {
                  val configurable = settings.find(configId)
                  settings.select(configurable).doWhenDone(Runnable {
                    if (searchTextField != null && search != null) searchTextField.text = search
                  })
                }
                else {
                  val project = context.getData<Project?>(CommonDataKeys.PROJECT)
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

      myBeforeWrapperPanel.isVisible = !actionMetaData.isSkipBeforeAfter
      myAfterWrapperPanel.isVisible = !actionMetaData.isSkipBeforeAfter
      showUsages(myBeforePanel, myBeforeUsagePanels, actionMetaData.getExampleUsagesBefore())
      showUsages(myAfterPanel, myAfterUsagePanels, actionMetaData.getExampleUsagesAfter())

      SwingUtilities.invokeLater(Runnable { component.revalidate() })
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  fun reset(intentionCategory: String) {
    try {
      myDescriptionBrowser
        .readHTML(CodeInsightBundle.message("intention.settings.category.text", intentionCategory))

      val beforeTemplate: TextDescriptor =
        PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.before"), BEFORE_TEMPLATE)
      showUsages(myBeforePanel, myBeforeUsagePanels, arrayOf<TextDescriptor>(beforeTemplate))
      val afterTemplate: TextDescriptor =
        PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.after"), AFTER_TEMPLATE)
      myBeforeWrapperPanel.isVisible = true
      myAfterWrapperPanel.isVisible = true
      showUsages(myAfterPanel, myAfterUsagePanels, arrayOf<TextDescriptor>(afterTemplate))

      SwingUtilities.invokeLater(Runnable { component.revalidate() })
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
    private const val BEFORE_TEMPLATE: @NonNls String = "before.java.template"
    private const val AFTER_TEMPLATE: @NonNls String = "after.java.template"
    private const val DIVIDER_PROPORTION_DEFAULT = .25f

    @Throws(IOException::class)
    private fun showUsages(
      panel: JPanel,
      usagePanels: MutableList<IntentionUsagePanel>,
      exampleUsages: Array<TextDescriptor>?
    ) {
      var gb: GridBagConstraints? = null
      val reuse = exampleUsages != null && panel.components.size == exampleUsages.size
      if (!reuse) {
        disposeUsagePanels(usagePanels)
        panel.setLayout(GridBagLayout())
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

          val usagePanel: IntentionUsagePanel
          if (reuse) {
            usagePanel = panel.getComponent(i) as IntentionUsagePanel
          }
          else {
            usagePanel = IntentionUsagePanel()
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

    private fun disposeUsagePanels(usagePanels: MutableList<out IntentionUsagePanel>) {
      for (usagePanel in usagePanels) {
        Disposer.dispose(usagePanel)
      }
      usagePanels.clear()
    }
  }
}