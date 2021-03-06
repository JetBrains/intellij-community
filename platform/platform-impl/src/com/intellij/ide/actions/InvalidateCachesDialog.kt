// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.google.common.collect.Sets
import com.intellij.ide.IdeBundle
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.SwingConstants

class InvalidateCachesDialog(
  project: Project?,
  private val canRestart: Boolean,
  invalidators: List<CachesInvalidator>,
) : DialogWrapper(project) {
  private val JUST_RESTART_CODE = NEXT_USER_EXIT_CODE + 3

  private val invalidatorsWithDescriptor = invalidators
    .mapNotNull { it.description?.to(it) }
    .toSortedSet(compareBy(String.CASE_INSENSITIVE_ORDER, { it.first }))

  val enabledInvalidators: MutableSet<CachesInvalidator> = Sets.newIdentityHashSet<CachesInvalidator>().apply {
    this += invalidators.filter { it.description == null }
  }

  init {
    title = IdeBundle.message("dialog.title.invalidate.caches")
    setResizable(false)
    init()
  }

  fun isRestartOnly() = canRestart && exitCode == JUST_RESTART_CODE

  private var Action.text
    get() = getValue(Action.NAME)
    set(value) = putValue(Action.NAME, value)


  override fun createActions(): Array<Action> {
    okAction.text = when {
      canRestart -> IdeBundle.message("button.invalidate.and.restart")
      else -> IdeBundle.message("button.invalidate.and.exit")
    }

    cancelAction.text = IdeBundle.message("button.cancel.without.mnemonic")

    val justRestartAction = if (canRestart) {
      DialogWrapperExitAction(IdeBundle.message("button.just.restart"), JUST_RESTART_CODE)
    } else null

    return listOfNotNull(okAction, cancelAction, justRestartAction).toTypedArray()
  }

  override fun createCenterPanel() = panel {
    val commentComponent = ComponentPanelBuilder.createCommentComponent(
      IdeBundle.message("dialog.message.caches.will.be.invalidated", ""),
      true,
      -1,
      true
    )
    commentComponent.icon = Messages.getWarningIcon()

    val component = JBLabel(IdeBundle.message("dialog.message.caches.will.be.invalidated"), Messages.getWarningIcon(), SwingConstants.LEFT)
    createNoteOrCommentRow(component)

    if (invalidatorsWithDescriptor.isNotEmpty()) {
      row {
        label(IdeBundle.message("dialog.message.the.following.items"))

        for ((text, descr) in invalidatorsWithDescriptor) {
          row {
            val defaultValue = descr.optionalCheckboxDefaultValue()

            val updateSelected = { cb: JCheckBox ->
              when {
                cb.isSelected -> enabledInvalidators += descr
                else -> enabledInvalidators -= descr
              }
            }

            val isSelected = defaultValue ?: true

            checkBox(text, isSelected,
                     comment = descr.comment,
                     actionListener = { _, cb -> updateSelected(cb) }
            ).component.apply {
              isEnabled = defaultValue != null
              updateSelected(this)
            }
          }
        }
      }
    }
  }
}
