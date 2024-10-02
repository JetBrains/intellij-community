// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.CatchingConsumer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent

@ApiStatus.Internal
class ManageRepoDialog(project: Project?, private val controller: PackageManagementService) : DialogWrapper(project, false) {
  private var myEnabled = false

  init {
    init()
    title = IdeBundle.message("manage.repositories.dialog.title")
  }

  override fun getDimensionServiceKey() = this::class.java.name + ".DimensionServiceKey"

  override fun createCenterPanel(): JComponent {
    val myList = JBList<String?>()
    myList.setPaintBusy(true)
    val repoModel = DefaultListModel<String?>()
    controller.fetchAllRepositories(object : CatchingConsumer<List<String?>, Exception> {
      override fun consume(repoUrls: List<String?>) {
        ApplicationManager.getApplication().invokeLater({
                                                          if (isDisposed) return@invokeLater
                                                          myList.setPaintBusy(false)
                                                          for (repoUrl: @NlsSafe String? in repoUrls) {
                                                            repoModel.addElement(repoUrl)
                                                          }
                                                        }, ModalityState.any())
      }

      override fun consume(e: Exception) {
        ApplicationManager.getApplication().invokeLater {
          if (isDisposed) return@invokeLater
          myList.setPaintBusy(false)
          LOG.warn(e)
        }
      }
    })
    myList.setModel(repoModel)
    myList.selectionMode = ListSelectionModel.SINGLE_SELECTION

    myList.addListSelectionListener { event: ListSelectionEvent? ->
      val selected = myList.selectedValue
      myEnabled = controller.canModifyRepository(selected)
    }

    val decorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions()
    decorator.setAddActionName(IdeBundle.message("action.add.repository"))
    decorator.setRemoveActionName(IdeBundle.message("action.remove.repository.from.list"))
    decorator.setEditActionName(IdeBundle.message("action.edit.repository.url"))

    decorator.setAddAction {
      val url = Messages.showInputDialog(IdeBundle.message("please.input.repository.url"), IdeBundle.message("repository.url.title"), null)
      if (!url.isNullOrBlank() && !repoModel.contains(url)) {
        repoModel.addElement(url)
        controller.addRepository(url)
      }
    }
    decorator.setEditAction {
      val oldValue = myList.selectedValue
      val url = Messages.showInputDialog(IdeBundle.message("please.edit.repository.url"), IdeBundle.message("repository.url.title"), null,
                                         oldValue, object : InputValidator {
        override fun checkInput(inputString: String): Boolean {
          return !repoModel.contains(inputString)
        }

        override fun canClose(inputString: String): Boolean {
          return true
        }
      })
      if (!url.isNullOrBlank() && oldValue != url) {
        repoModel.addElement(url)
        repoModel.removeElement(oldValue)
        controller.removeRepository(oldValue)
        controller.addRepository(url)
      }
    }
    decorator.setRemoveAction { button ->
      val selected = myList.selectedValue
      controller.removeRepository(selected)
      repoModel.removeElement(selected)
      button.isEnabled = false
    }
    decorator.setRemoveActionUpdater { myEnabled }
    decorator.setEditActionUpdater { myEnabled }

    return decorator.createPanel().apply {
      preferredSize = JBUI.size(600, 400)
    }
  }

  companion object {
    private val LOG = thisLogger()
  }
}