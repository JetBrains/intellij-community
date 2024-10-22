// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages

import com.intellij.ide.util.DirectoryUtil
import com.intellij.java.JavaBundle
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.refactoring.*
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveDialogBase
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.canBeOpenedInEditor
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

open class MoveClassesOrPackagesToNewDirectoryDialog(
  private val directory: PsiDirectory,
  private val elementsToMove: Array<out PsiElement>,
  private val canShowPreserveSourceRoots: Boolean,
  private val moveCallback: MoveCallback?,
) : MoveDialogBase(directory.project, false, canBeOpenedInEditor(elementsToMove)) {
  companion object {
    private val LOG = Logger.getInstance(MoveClassesOrPackagesToNewDirectoryDialog::class.java)
  }

  constructor(directory: PsiDirectory, elementsToMove: Array<out PsiElement>, moveCallback: MoveCallback?) :
    this(directory, elementsToMove, true, moveCallback)

  init {
    title = MoveHandler.getRefactoringName()
    init()
    setupComponents()
  }

  private fun setupComponents() {
    val project = directory.project
    val refactoringSettings = JavaRefactoringSettings.getInstance()
    val sourceNameText = getElementsPath(elementsToMove)
    sourceNameLabel.text = HtmlChunk.html().addRaw(sourceNameText).toString()
    searchInCommentsAndStringsCheckBox.isSelected = refactoringSettings.MOVE_SEARCH_IN_COMMENTS
    searchForTextOccurrencesCheckBox.isSelected = refactoringSettings.MOVE_SEARCH_FOR_TEXT

    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION)
    )
    tooltipLabel.text = RefactoringBundle.message("path.completion.shortcut", shortcutText)

    if (canShowPreserveSourceRoots) {
      val sourceRoots = mutableSetOf<VirtualFile?>()
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      val destinationModule = fileIndex.getModuleForFile(directory.virtualFile)
      var sameModule = true
      for (element in elementsToMove) {
        when (element) {
          is PsiPackage -> {
            for (psiDirectory in element.getDirectories()) {
              val virtualFile = psiDirectory.virtualFile
              sourceRoots.add(fileIndex.getSourceRootForFile(virtualFile))
              //sameModule = sameModule && (destinationModule == fileIndex.getModuleForFile(it))
            }
          }
          is PsiClass -> {
            val virtualFile = element.containingFile.virtualFile
            virtualFile?.let {
              sourceRoots.add(fileIndex.getSourceRootForFile(it))
              sameModule = sameModule && (destinationModule == fileIndex.getModuleForFile(it))
            }
          }
        }
      }
      preserveSourceRootCheckBox.isVisible = sourceRoots.size > 1
      preserveSourceRootCheckBox.isSelected = sameModule
    }
    else if (elementsToMove.size < 2) {
      preserveSourceRootCheckBox.isVisible = false
      preserveSourceRootCheckBox.isSelected = false
    }
  }

  @NlsSafe
  private fun getElementsPath(elements: Array<out PsiElement>): String = elements.joinToString("<br/>") { element ->
    if (element is PsiFileSystemItem) {
      "../" + SymbolPresentationUtil.getFilePathPresentation(element)
    }
    else {
      SymbolPresentationUtil.getSymbolPresentableText(element)
    }
  }

  private lateinit var destDirectoryField: TextFieldWithBrowseButton
  private lateinit var searchInCommentsAndStringsCheckBox: JCheckBox
  private lateinit var searchForTextOccurrencesCheckBox: JCheckBox
  private lateinit var preserveSourceRootCheckBox: JCheckBox
  private lateinit var sourceNameLabel: JLabel
  private lateinit var tooltipLabel: JBLabel

  override fun createCenterPanel(): JComponent {
    val project = directory.project
    return panel {
      customizeSpacingConfiguration(createSpacingConfiguration()) {
        row(JavaRefactoringBundle.message("move.label.text")) {
          sourceNameLabel = label("").applyToComponent {
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
          }.resizableColumn().component
        }
        row(JavaRefactoringBundle.message("move.files.to.new.directory.prompt")) {
          destDirectoryField = textFieldWithBrowseButton(
            project = project,
            fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            fileChosen = { file -> FileUtil.toSystemDependentName(file.path) }
          ).applyToComponent {
            text = FileUtil.toSystemDependentName(directory.virtualFile.path)
            maximumSize = Dimension(400, preferredSize.height)
            textField.document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                isOKActionEnabled = text.isNotEmpty()
              }
            })
          }.resizableColumn().align(AlignX.FILL).component
        }
        row("") {
          tooltipLabel = JBLabel().apply {
            componentStyle = UIUtil.ComponentStyle.SMALL
            fontColor = UIUtil.FontColor.BRIGHTER
            border = JBUI.Borders.emptyLeft(10)
          }
          cell(tooltipLabel)
        }
        row {
          searchInCommentsAndStringsCheckBox = checkBox(RefactoringBundle.message("search.in.comments.and.strings"))
            .resizableColumn().component
        }
        row {
          searchForTextOccurrencesCheckBox = checkBox(RefactoringBundle.message("search.for.text.occurrences"))
            .resizableColumn().component
        }
        row {
          preserveSourceRootCheckBox = checkBox(JavaBundle.message("leave.in.same.source.root.item"))
            .resizableColumn().component
        }
      }
    }
  }

  override fun doAction() {
    val path = FileUtil.toSystemIndependentName(destDirectoryField.text)
    val project = this@MoveClassesOrPackagesToNewDirectoryDialog.directory.project
    val directory = WriteAction.compute<PsiDirectory?, Throwable> {
      try {
        return@compute DirectoryUtil.mkdirs(PsiManager.getInstance(project), path)
      }
      catch (e: IncorrectOperationException) {
        LOG.error(e)
        return@compute null
      }
    }
    if (directory == null) {
      Messages.showErrorDialog(project, JavaRefactoringBundle.message("cannot.find.or.create.destination.directory"),
                               JavaRefactoringBundle.message("cannot.move"))
      return
    }

    val aPackage = JavaDirectoryService.getInstance().getPackage(directory)
    if (aPackage == null) {
      Messages.showErrorDialog(project, JavaRefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               JavaRefactoringBundle.message("cannot.move")
      )
      return
    }

    val refactoringSettings = JavaRefactoringSettings.getInstance()
    val searchInComments = searchInCommentsAndStringsCheckBox.isSelected
    val searchForTextOccurrences = searchForTextOccurrencesCheckBox.isSelected
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurrences

    createRefactoringProcessor(project, directory, aPackage, searchInComments, searchForTextOccurrences)?.let {
      invokeRefactoring(it)
    }
  }

  //for scala plugin
  protected open fun createMoveClassesOrPackagesProcessor(
    project: Project?,
    elements: Array<out PsiElement>,
    moveDestination: MoveDestination,
    searchInComments: Boolean,
    searchInNonJavaFiles: Boolean,
    moveCallback: MoveCallback?,
  ) = MoveClassesOrPackagesProcessor(project, elements, moveDestination,
                                     searchInComments, searchInNonJavaFiles, moveCallback)

  protected open fun createRefactoringProcessor(
    project: Project?,
    directory: PsiDirectory,
    aPackage: PsiPackage,
    searchInComments: Boolean,
    searchForTextOccurrences: Boolean,
  ): BaseRefactoringProcessor? {
    val destination = createDestination(aPackage, directory) ?: return null
    val processor = createMoveClassesOrPackagesProcessor(
      project,
      elementsToMove,
      destination,
      searchInComments,
      searchForTextOccurrences,
      moveCallback
    )
    processor.setOpenInEditor(isOpenInEditor)
    return if (processor.verifyValidPackageName()) processor else null
  }

  protected open fun createDestination(aPackage: PsiPackage, directory: PsiDirectory): MoveDestination? {
    val project = aPackage.project
    val sourceRoot = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(directory.virtualFile)
    if (sourceRoot == null) {
      Messages.showErrorDialog(
        project,
        JavaRefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
        JavaRefactoringBundle.message("cannot.move")
      )
      return null
    }

    val factory = JavaRefactoringFactory.getInstance(project)
    return if (preserveSourceRootCheckBox.isSelected && preserveSourceRootCheckBox.isVisible)
      factory.createSourceFolderPreservingMoveDestination(aPackage.qualifiedName)
    else
      factory.createSourceRootMoveDestination(aPackage.qualifiedName, sourceRoot)
  }

  protected open fun createSpacingConfiguration(): SpacingConfiguration = IntelliJSpacingConfiguration()

  override fun getPreferredFocusedComponent(): JComponent = destDirectoryField.textField

  override fun getHelpId(): String = "refactoring.moveFile"
}
