// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupShowOptions
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.TextWidgetPresentation
import com.intellij.openapi.wm.WidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.WidgetPresentationFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.popup.PopupState
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.asDisposable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.RestartDialogImpl
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal class ScreenReaderSupportWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
  init {
    service<ScreenReaderSupportWidgetUpdater>()
  }

  override fun getId(): String = "ScreenReaderSupport"

  override fun getDisplayName(): String = message("status.bar.widget.screen.reader.support")

  override fun isAvailable(project: Project): Boolean = ScreenReader.isActive()

  override fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope): WidgetPresentation =
    ScreenReaderSupportWidget(context.project)
}

@Service(Service.Level.APP)
private class ScreenReaderSupportWidgetUpdater(scope: CoroutineScope) {
  init {
    ScreenReader.addPropertyChangeListener(ScreenReader.SCREEN_READER_ACTIVE_PROPERTY, scope.asDisposable()) {
      for (project in ProjectManager.getInstance().openProjects) {
        project.serviceIfCreated<StatusBarWidgetsManager>()?.updateWidget(ScreenReaderSupportWidgetFactory::class.java)
      }
    }
  }
}

private class ScreenReaderSupportWidget(private val project: Project) : TextWidgetPresentation {
  private val popupState = PopupState.forPopup()

  override val alignment: Float = Component.CENTER_ALIGNMENT

  override fun text(): Flow<String> = flowOf(message("status.bar.widget.screen.reader.support"))

  override fun getClickConsumer(): (MouseEvent) -> Unit = { event ->
    if (!popupState.isRecentlyHidden) {
      showPopup(event.component)
    }
  }

  private fun showPopup(widget: Component) {
    val popup = ScreenReaderSupportPopup(project, ScreenReaderSupportStep(project))
    popupState.prepareToShow(popup)
    popup.show(PopupShowOptions.aboveComponentRightAligned(widget))
    AccessibleAnnouncerUtil.announce(popup.list, message("status.bar.widget.screen.reader.support.popup.text"), false)
  }
}

private enum class ScreenReaderSupportItem { CONFIGURE, DISABLE }

private class ScreenReaderSupportStep(private val project: Project) : BaseListPopupStep<ScreenReaderSupportItem>(
  message("status.bar.widget.screen.reader.support.popup.title"), ScreenReaderSupportItem.entries,
) {
  private val overridden = isSupportScreenReadersOverridden()

  override fun getTextFor(value: ScreenReaderSupportItem): String = when (value) {
    ScreenReaderSupportItem.CONFIGURE -> message("status.bar.widget.screen.reader.support.popup.configure")
    ScreenReaderSupportItem.DISABLE -> message("status.bar.widget.screen.reader.support.popup.disable")
  }

  override fun isSelectable(value: ScreenReaderSupportItem): Boolean = value != ScreenReaderSupportItem.DISABLE || !overridden

  override fun onChosen(selectedValue: ScreenReaderSupportItem, finalChoice: Boolean): PopupStep<*>? = doFinalStep {
    when (selectedValue) {
      ScreenReaderSupportItem.CONFIGURE -> ShowSettingsUtil.getInstance().showSettingsDialog(project, AccessibilityConfigurable::class.java)
      ScreenReaderSupportItem.DISABLE -> {
        GeneralSettings.getInstance().isSupportScreenReaders = false
        RestartDialogImpl.showRestartRequired()
      }
    }
  }
}

private class ScreenReaderSupportPopup(project: Project, step: ListPopupStep<*>) : ListPopupImpl(project, step) {
  override fun createPopupComponent(content: JComponent): JComponent {
    val list = super.createPopupComponent(content)
    val text = panel {
      row {
        text(message("status.bar.widget.screen.reader.support.popup.text"), maxLineLength = 32)
          .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
      }
    }
    text.isOpaque = false
    text.border = JBUI.Borders.empty(0, 16)
    val header = JPanel(BorderLayout())
    header.isOpaque = false
    header.border = JBUI.Borders.emptyBottom(6)
    header.add(text, BorderLayout.CENTER)
    header.add(GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets()), BorderLayout.SOUTH)
    return JPanel(BorderLayout()).apply {
      PopupUtil.applyNewUIBackground(this)
      add(header, BorderLayout.NORTH)
      add(list, BorderLayout.CENTER)
    }
  }
}
