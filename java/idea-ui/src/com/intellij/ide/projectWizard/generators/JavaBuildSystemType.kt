// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.BuildSystemType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*

abstract class JavaBuildSystemType<P>(override val name: String) : BuildSystemType<JavaSettings, P> {
  companion object {
    var EP_NAME = ExtensionPointName<JavaBuildSystemType<*>>("com.intellij.newProjectWizard.buildSystem.java")
  }
}

class IntelliJJavaBuildSystemType : JavaBuildSystemType<IntelliJBuildSystemSettings>("IntelliJ") {
  override var settingsFactory = { IntelliJBuildSystemSettings() }

  override fun advancedSettings(settings: IntelliJBuildSystemSettings): DialogPanel =
    panel {
      hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
        row {
          cell { label(UIBundle.message("label.project.wizard.new.project.module.name")) }
          cell {
            textField(settings::moduleName)
          }
        }

        row {
          cell { label(UIBundle.message("label.project.wizard.new.project.content.root")) }
          cell {
            textFieldWithBrowseButton(settings::contentRoot, UIBundle.message("label.project.wizard.new.project.content.root"), null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }

        row {
          cell { label(UIBundle.message("label.project.wizard.new.project.module.file.location")) }
          cell {
            textFieldWithBrowseButton(settings::moduleFileLocation,
                                      UIBundle.message("label.project.wizard.new.project.module.file.location"), null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }
      }
    }

  override fun setupProject(project: Project, languageSettings: JavaSettings, settings: IntelliJBuildSystemSettings) {
    TODO("Not yet implemented")
  }
}

class IntelliJBuildSystemSettings {
  var moduleName: String = ""
  var contentRoot: String = ""
  var moduleFileLocation: String = ""
}