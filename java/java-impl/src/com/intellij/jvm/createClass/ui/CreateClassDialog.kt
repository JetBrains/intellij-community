/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.jvm.createClass.ui

import com.intellij.CommonBundle
import com.intellij.codeInsight.CodeInsightBundle.message
import com.intellij.codeInsight.intention.impl.CreateClassDialog.RECENTS_KEY
import com.intellij.ide.util.PackageUtil
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.lang.Language
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.refactoring.util.RefactoringMessageUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.RecentsManager
import com.intellij.ui.components.JBTextField
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JPanel

class CreateClassDialog(private val project: Project, private val myModule: Module?) : DialogWrapper(project) {

  private lateinit var myKindsMap: Map<Language, List<SourceClassKind>>

  private val myLanguageCombo = ComboBox<Language>().apply {
    renderer = LanguageListCellRenderer()
    addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        languageChanged()
      }
    }
  }
  private val myLanguageRow = LabeledComponent<JComponent>().apply {
    text = message("dialog.create.class.label.language")
    labelLocation = BorderLayout.WEST
    component = myLanguageCombo
  }
  private var mySingleLanguage: Language? = null
  private val mySelectedLanguage: Language get() = mySingleLanguage ?: myLanguageCombo.selectedItem as Language

  private val myClassKindCombo = ComboBox<SourceClassKind>().apply {
    renderer = ClassKindListCellRenderer()
  }
  private val myClassKindRow = LabeledComponent<JComponent>().apply {
    text = message("dialog.create.class.label.kind")
    labelLocation = BorderLayout.WEST
    component = myClassKindCombo
  }
  private var mySingleKind: SourceClassKind? = null
  private val mySelectedClassKind: SourceClassKind get() {
    if (mySingleKind == null) {
      // regular bahaviour
      return myClassKindCombo.selectedItem as SourceClassKind
    }
    else {
      // each language has single kind with same diplayName
      return myKindsMap[mySelectedLanguage]!!.single()
    }
  }

  private val myPackageComponent = PackageNameReferenceEditorCombo(
    null, project, RECENTS_KEY, message("dialog.create.class.package.chooser.title")
  ).apply {
    setTextFieldPreferredWidth(40)
  }
  private val myPackageRow = LabeledComponent<JComponent>().apply {
    text = message("dialog.create.class.label.package")
    labelLocation = BorderLayout.WEST
    component = myPackageComponent
  }

  private val myClassNameField = JBTextField()
  private val myClassNameRow: LabeledComponent<JComponent> = LabeledComponent<JComponent>().apply {
    text = message("dialog.create.class.label.name")
    labelLocation = BorderLayout.WEST
    component = myClassNameField
  }
  private var myClassNameEditable: Boolean = true
  private lateinit var myInitialClassName: String
  private val myClassName: String get() {
    return if (myClassNameEditable) myClassNameField.text else myInitialClassName
  }

  private val myDestinationFolderCombo = object : DestinationFolderComboBox() {
    override fun getTargetPackage(): String = myPackageComponent.text.trim { it <= ' ' }
  }
  private val myDestinationFolderRow = LabeledComponent<JComponent>().apply {
    text = message("dialog.create.class.label.directory")
    labelLocation = BorderLayout.WEST
    component = myDestinationFolderCombo
  }
  private var myTargetDirectory: PsiDirectory? = null

  private fun languageChanged() {
    val languageKinds = myKindsMap[mySelectedLanguage]!!.toList()
    myClassKindCombo.model = CollectionComboBoxModel<SourceClassKind>(languageKinds)
  }

  fun initKinds(kindsMap: Map<Language, List<SourceClassKind>>) {
    require(kindsMap.isNotEmpty())
    myKindsMap = kindsMap
    mySingleLanguage = myKindsMap.keys.singleOrNull()
    mySingleKind = computeSingleKind(kindsMap)
    myLanguageCombo.model = CollectionComboBoxModel(myKindsMap.keys.toList())
    languageChanged()
  }

  fun initClassName(editable: Boolean, className: String) {
    myClassNameField.text = className
    myClassNameEditable = editable
    myInitialClassName = className
  }

  fun initTargetPackage(targetPackageName: String?) {
    myDestinationFolderCombo.setData(project, getBaseDir(targetPackageName), object : Pass<String>() {
      override fun pass(s: String?) {
        setErrorText(s, myDestinationFolderCombo)
      }
    }, myPackageComponent.childComponent)
  }

  public override fun init() {
    val title = StringBuilder().append("Create")
    mySingleLanguage?.let {
      title.append(' ').append(it.displayName)
    }
    title.append(' ')
    mySingleKind?.let { title.append(it.displayName) } ?: title.append("Class")
    if (!myClassNameEditable) {
      title.append(" '").append(myInitialClassName).append('\'')
    }
    setTitle(title.toString())
    super.init()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    if (mySingleLanguage == null) return myLanguageCombo
    if (mySingleKind == null) return myClassKindCombo
    if (myClassNameEditable) return myClassNameField
    return myPackageComponent.childComponent
  }

  override fun createCenterPanel(): JComponent? {
    val panel = JPanel()
    val layout = VerticalFlowLayout(0, 8)
    panel.layout = layout

    val visibleRows = mutableListOf<PanelWithAnchor>()
    fun add(row: LabeledComponent<*>) {
      panel.add(row)
      visibleRows.add(row)
    }

    if (mySingleLanguage == null) {
      add(myLanguageRow)
    }

    if (mySingleKind == null) {
      add(myClassKindRow)
    }

    if (myClassNameEditable) {
      add(myClassNameRow)
    }

    add(myPackageRow)
    add(myDestinationFolderRow)

    UIUtil.mergeComponentsWithAnchor(visibleRows)

    return panel
  }

  override fun doOKAction() {
    RecentsManager.getInstance(project).registerRecentEntry(RECENTS_KEY, myPackageComponent.text)
    val packageName = myPackageComponent.text?.trim() ?: ""

    var errorString: String? = null
    CommandProcessor.getInstance().executeCommand(project, {
      try {
        val targetPackage = PackageWrapper(PsiManager.getInstance(project), packageName)
        val destination = myDestinationFolderCombo.selectDirectory(targetPackage, false) ?: return@executeCommand
        myTargetDirectory = runWriteAction {
          val baseDir = getBaseDir(packageName)
          if (baseDir == null && destination is MultipleRootsMoveDestination) {
            errorString = "Destination not found for package '$packageName'"
            return@runWriteAction null
          }
          destination.getTargetDirectory(baseDir)
        }
        if (myTargetDirectory == null) {
          return@executeCommand
        }
        errorString = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, myClassName)
      }
      catch (e: IncorrectOperationException) {
        errorString = e.message
      }
    }, message("create.directory.command"), null)

    errorString?.let {
      if (it.isNotEmpty()) {
        Messages.showMessageDialog(project, errorString, CommonBundle.getErrorTitle(), Messages.getErrorIcon())
        return
      }
    }
    super.doOKAction()
  }

  private fun getBaseDir(packageName: String?): PsiDirectory? {
    return if (myModule == null) null else PackageUtil.findPossiblePackageDirectoryInModule(myModule, packageName)
  }

  private fun computeSingleKind(kindsMap: Map<Language, List<SourceClassKind>>): SourceClassKind? {
    var singleKind: SourceClassKind? = null
    for ((@Suppress("UNUSED_VARIABLE") language, kinds) in kindsMap) {
      val languageKind = kinds.singleOrNull() ?: return null
      if (singleKind == null) {
        singleKind = languageKind
      }
      else if (singleKind.displayName != languageKind.displayName) {
        return null
      }
    }
    return singleKind
  }

  val userInfo: CreateClassUserInfo? get() {
    val targetDirectory = myTargetDirectory ?: return null
    return CreateClassUserInfo(
      mySelectedClassKind,
      myClassName,
      targetDirectory
    )
  }
}
