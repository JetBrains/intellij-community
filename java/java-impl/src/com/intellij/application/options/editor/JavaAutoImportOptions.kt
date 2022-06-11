// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.CodeInsightWorkspaceSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import javax.swing.JComponent

class JavaAutoImportOptions(val project: Project) : UiDslUnnamedConfigurable.Simple(), AutoImportOptionsProvider {
  private val excludeTable = ExcludeTable(project)

  override fun Panel.createContent() {
    excludeTable.tableView.setShowGrid(false)
    val dcaSettings = DaemonCodeAnalyzerSettings.getInstance()
    val ciSettings = CodeInsightSettings.getInstance()
    val ciWorkspaceSettings = CodeInsightWorkspaceSettings.getInstance(project)
    lateinit var dataContextOwner: JComponent

    group(JavaLanguage.INSTANCE.displayName) {
      row(JavaBundle.message("label.show.import.popup.for")) {
        checkBox(JavaBundle.message("show.import.popup.for.classes"))
          .bindSelected({ dcaSettings.isImportHintEnabled }, { dcaSettings.isImportHintEnabled = it })
        checkBox(JavaBundle.message("show.import.popup.for.static.methods.and.fields"))
          .bindSelected(ciSettings::ADD_MEMBER_IMPORTS_ON_THE_FLY)
      }.layout(RowLayout.INDEPENDENT)
      row(JavaBundle.message("combobox.paste.insert.imports")) {
        comboBox(
          CollectionComboBoxModel(listOf(CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK)),
          listCellRenderer { value, _, _ ->
            setText(when (value) {
                      CodeInsightSettings.YES -> ApplicationBundle.message("combobox.insert.imports.all")
                      CodeInsightSettings.NO -> ApplicationBundle.message("combobox.insert.imports.none")
                      CodeInsightSettings.ASK -> ApplicationBundle.message("combobox.insert.imports.ask")
                      else -> ""
                    })
          }
        ).bindItem(ciSettings::ADD_IMPORTS_ON_PASTE.toNullableProperty())
      }
      row {
        checkBox(ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"))
          .bindSelected(ciSettings::ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY)
          .gap(RightGap.SMALL)
        contextHelp(ApplicationBundle.message("help.add.unambiguous.imports"))
      }
      row {
        checkBox(ApplicationBundle.message("checkbox.optimize.imports.on.the.fly"))
          .bindSelected({ ciWorkspaceSettings.isOptimizeImportsOnTheFly }, { ciWorkspaceSettings.isOptimizeImportsOnTheFly = it })
          .also { dataContextOwner = it.component }
          .gap(RightGap.SMALL)
        cell(ContextHelpLabel.createWithLink(
          null,
          ApplicationBundle.message("help.optimize.imports.on.the.fly"),
          ApplicationBundle.message("help.link.optimize.imports.on.the.fly")
        ) { openJavaImportSettings(dataContextOwner) })
          .gap(RightGap.SMALL)
        icon(AllIcons.General.ProjectConfigurable)
          .applyToComponent {
            toolTipText = IdeUICustomization.getInstance().projectMessage("configurable.current.project.tooltip")
          }
      }
      row {
        cell(excludeTable.component)
          .horizontalAlign(HorizontalAlign.FILL)
          .label(JavaBundle.message("exclude.from.completion.group"), LabelPosition.TOP)
          .comment(JavaBundle.message("exclude.import.wildcard.comment"))
          .onApply { excludeTable.apply() }
          .onReset { excludeTable.reset() }
          .onIsModified { excludeTable.isModified }
      }
    }
    onApply {
      for (project in ProjectManager.getInstance().openProjects) {
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    }
  }

  private fun openJavaImportSettings(dataContextOwner: JComponent) {
    Settings.KEY.getData(DataManager.getInstance().getDataContext(dataContextOwner))?.let { settings ->
      (settings.find("preferences.sourceCode.Java") as? CodeStyleConfigurableWrapper)?.let { configurable ->
        settings.select(configurable).doWhenDone {
          configurable.selectTab(ApplicationBundle.message("title.imports"))
        }
      }
    }
  }

  fun addExcludePackage(packageName: String) {
    excludeTable.addExcludePackage(packageName)
  }
}