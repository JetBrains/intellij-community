// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.CodeInsightWorkspaceSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.ide.DataManager
import com.intellij.ide.JavaLanguageCodeStyleSettingsProvider
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.layout.*
import javax.swing.JComponent

class JavaAutoImportOptions(val project: Project) : DslConfigurableBase(), AutoImportOptionsProvider {
  private val excludeTable = ExcludeTable(project)

  override fun createPanel(): DialogPanel {
    val dcaSettings = DaemonCodeAnalyzerSettings.getInstance()
    val ciSettings = CodeInsightSettings.getInstance()
    val ciWorkspaceSettings = CodeInsightWorkspaceSettings.getInstance(project)
    lateinit var dataContextOwner: JComponent
    return panel {
      titledRow("Java") {
        row {
          cell {
            label(JavaBundle.message("label.show.import.popup.for"))
            checkBox(JavaBundle.message("show.import.popup.for.classes"), { dcaSettings.isImportHintEnabled },
                     { dcaSettings.isImportHintEnabled = it })
            checkBox(JavaBundle.message("show.import.popup.for.static.methods.and.fields"), ciSettings::ADD_MEMBER_IMPORTS_ON_THE_FLY)
          }
        }
        row {
          cell {
            label(JavaBundle.message("combobox.paste.insert.imports"))
            comboBox(
              CollectionComboBoxModel(listOf(CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK)),
              ciSettings::ADD_IMPORTS_ON_PASTE,
              listCellRenderer { value, _, _ ->
                setText(when (value) {
                          CodeInsightSettings.YES -> ApplicationBundle.message("combobox.insert.imports.all")
                          CodeInsightSettings.NO -> ApplicationBundle.message("combobox.insert.imports.none")
                          CodeInsightSettings.ASK -> ApplicationBundle.message("combobox.insert.imports.ask")
                          else -> ""
                        })
              }
            )
          }
        }
        row {
          checkBox(ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"), ciSettings::ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY)
        }
        row {
          cell {
            checkBox(ApplicationBundle.message("checkbox.optimize.imports.on.the.fly"), { ciWorkspaceSettings.isOptimizeImportsOnTheFly },
                     { ciWorkspaceSettings.isOptimizeImportsOnTheFly = it }).also { dataContextOwner = it.component }
            ContextHelpLabel.createWithLink(
              null,
              ApplicationBundle.message("help.optimize.imports.on.the.fly"),
              ApplicationBundle.message("help.link.optimize.imports.on.the.fly")
            ) { openJavaImportSettings(dataContextOwner) }()
            comment(IdeUICustomization.getInstance().projectMessage("configurable.current.project.tooltip"))
          }
        }
        row {
          label(JavaBundle.message("exclude.from.completion.group"))
        }
        row {
          excludeTable.component(CCFlags.grow, comment = JavaBundle.message("exclude.import.wildcard.comment"))
            .onApply { excludeTable.apply() }
            .onReset { excludeTable.reset() }
            .onIsModified { excludeTable.isModified }
        }
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

  override fun apply() {
    super.apply()
    for (project in ProjectManager.getInstance().openProjects) {
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }

  fun addExcludePackage(packageName: String) {
    excludeTable.addExcludePackage(packageName)
  }
}