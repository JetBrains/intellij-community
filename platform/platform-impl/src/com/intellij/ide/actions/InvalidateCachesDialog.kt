// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.google.common.collect.Sets
import com.intellij.ide.IdeBundle
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.Link
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JPanel

private var Action.text
  get() = getValue(Action.NAME)
  set(value) = putValue(Action.NAME, value)

class InvalidateCachesDialog(
  project: Project?,
  private val canRestart: Boolean,
  private val invalidators: List<CachesInvalidator>,
) : DialogWrapper(project) {
  private val JUST_RESTART_CODE = NEXT_USER_EXIT_CODE + 3

  private val enabledInvalidators: MutableSet<CachesInvalidator> = Sets.newIdentityHashSet()

  fun getSelectedInvalidators() : List<CachesInvalidator> {
    if (!isOK) return emptyList()

    return invalidators.filter {
      it.description == null || it in enabledInvalidators
    }
  }

  override fun getHelpId() = "invalidate-cache-restart"

  init {
    title = IdeBundle.message("dialog.title.invalidate.caches")
    setResizable(false)
    init()

    okAction.text = when {
      canRestart -> IdeBundle.message("button.invalidate.and.restart")
      else -> IdeBundle.message("button.invalidate.and.exit")
    }

    cancelAction.text = IdeBundle.message("button.cancel.without.mnemonic")
  }

  fun isRestartOnly() = canRestart && exitCode == JUST_RESTART_CODE

  override fun createSouthAdditionalPanel(): JPanel? {
    if (!canRestart) return null

    val link = Link(IdeBundle.message("link.just.restart")) {
      close(JUST_RESTART_CODE)
    }

    val panel = NonOpaquePanel(BorderLayout())
    panel.border = JBUI.Borders.emptyLeft(10)
    panel.add(link)
    return panel
  }

  override fun createCenterPanel() = panel {
    row {
      label(
        HtmlChunk
          .html()
          .addText(IdeBundle.message("dialog.message.caches.will.be.invalidated"))
          .toString()
      ).growPolicy(GrowPolicy.MEDIUM_TEXT).constraints(CCFlags.growY)
    }

    //we keep the original order as it comes from the extensions order
    val invalidatorsWithDescriptor = invalidators.mapNotNull { it.description?.to(it) }

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
