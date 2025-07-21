// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.CodeInsightWorkspaceSettings
import com.intellij.codeInsight.JavaIdeCodeInsightSettings
import com.intellij.codeInsight.JavaProjectCodeInsightSettings
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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JComponent

public class JavaAutoImportOptions(public val project: Project) : UiDslUnnamedConfigurable.Simple(), AutoImportOptionsProvider {
  private val excludeTable = object : ImportTable(project,
                                                  JavaBundle.message("exclude.from.imports.no.exclusions"),
                                                  JavaBundle.message("exclude.table.mask"),
                                                  JavaBundle.message("exclude.table.scope.column")) {
    override fun getIdeRows(): Array<out String> {
      return CodeInsightSettings.getInstance().EXCLUDED_PACKAGES
    }

    override fun getProjectRows(): Array<out String> {
      return JavaProjectCodeInsightSettings.getSettings(project).excludedNames.toTypedArray()
    }

    override fun setIdeRows(rows: Array<out String>) {
      CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = rows
    }

    override fun setProjectRows(rows: Array<out String>) {
      JavaProjectCodeInsightSettings.getSettings(project).excludedNames = rows.toList()
    }
  }

  private val autoStaticImportTable = object : ImportTable(project,
                                                           JavaBundle.message("auto.static.import.comment"),
                                                           JavaBundle.message("auto.static.import.class"),
                                                           JavaBundle.message("auto.static.import.scope")) {

    override fun getIdeRows(): Array<out String> {
      return JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames.toTypedArray()
    }

    override fun getProjectRows(): Array<out String> {
      return JavaProjectCodeInsightSettings.getSettings(project).includedAutoStaticNames.toTypedArray()
    }

    override fun setIdeRows(rows: Array<out String>) {
      JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames = rows.toList()
    }

    override fun setProjectRows(rows: Array<out String>) {
      JavaProjectCodeInsightSettings.getSettings(project).includedAutoStaticNames = rows.toList()
    }
  }

  override fun Panel.createContent() {
    excludeTable.tableView.setShowGrid(false)
    autoStaticImportTable.tableView.setShowGrid(false)

    val dcaSettings = DaemonCodeAnalyzerSettings.getInstance()
    val ciSettings = CodeInsightSettings.getInstance()
    val ciWorkspaceSettings = CodeInsightWorkspaceSettings.getInstance(project)
    lateinit var dataContextOwner: JComponent

    group(JavaLanguage.INSTANCE.displayName) {
      row(JavaBundle.message("label.show.import.popup.for")) {
        checkBox(JavaBundle.message("show.import.popup.for.classes"))
          .bindSelected(dcaSettings::isImportHintEnabled, dcaSettings::setImportHintEnabled)
        checkBox(JavaBundle.message("show.import.popup.for.static.methods.and.fields"))
          .bindSelected(ciSettings::ADD_MEMBER_IMPORTS_ON_THE_FLY)
      }.layout(RowLayout.INDEPENDENT)
      row(JavaBundle.message("combobox.paste.insert.imports")) {
        comboBox(
          listOf(CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK),
          textListCellRenderer {
            when (it) {
              CodeInsightSettings.YES -> ApplicationBundle.message("combobox.insert.imports.all")
              CodeInsightSettings.NO -> ApplicationBundle.message("combobox.insert.imports.none")
              CodeInsightSettings.ASK -> ApplicationBundle.message("combobox.insert.imports.ask")
              else -> ""
            }
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
          .bindSelected(ciWorkspaceSettings::isOptimizeImportsOnTheFly, ciWorkspaceSettings::setOptimizeImportsOnTheFly)
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
        cell(autoStaticImportTable.component)
          .align(AlignX.FILL)
          .label(JavaBundle.message("auto.static.import.completion.group"), LabelPosition.TOP)
          .comment(JavaBundle.message("auto.static.import.example"))
          .onApply { autoStaticImportTable.apply() }
          .onReset { autoStaticImportTable.reset() }
          .onIsModified { autoStaticImportTable.isModified }
      }
      row {
        cell(excludeTable.component)
          .align(AlignX.FILL)
          .label(JavaBundle.message("exclude.from.completion.group"), LabelPosition.TOP)
          .comment(JavaBundle.message("exclude.import.wildcard.comment"))
          .onApply { excludeTable.apply() }
          .onReset { excludeTable.reset() }
          .onIsModified { excludeTable.isModified }
          .applyToComponent {
            disposable?.let { Disposer.register(it, excludeTable) }
          }
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

  public fun addExcludePackage(packageName: String) {
    excludeTable.addRow(packageName)
  }
}