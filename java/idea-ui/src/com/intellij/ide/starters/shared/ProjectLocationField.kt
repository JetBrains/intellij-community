// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.shared

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText

fun Row.projectLocationField(locationProperty: GraphProperty<String>,
                             wizardContext: WizardContext): Cell<TextFieldWithBrowseButton> {
  val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter { it.isDirectory }
  val fileChosen = { file: VirtualFile -> getPresentablePath(file.path) }
  val title = IdeBundle.message("title.select.project.file.directory", wizardContext.presentationName)
  val property = locationProperty.transform(::getPresentablePath, ::getCanonicalPath)
  return this.textFieldWithBrowseButton(title, wizardContext.project, fileChooserDescriptor, fileChosen)
    .bindText(property)
}
