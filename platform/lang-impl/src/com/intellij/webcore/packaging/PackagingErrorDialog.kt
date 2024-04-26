// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging

import com.intellij.lang.LangBundle
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JTextArea
import javax.swing.JTextPane

class PackagingErrorDialog(title: @NlsContexts.DialogTitle String,
                           errorDescription: PackageManagementService.ErrorDescription) : DialogWrapper(false) {
  private val myDetailsVisible = AtomicBooleanProperty(false)
  private val myDetailsLabel: JBLabel = JBLabel()
  private val myDetails: JTextArea = JTextArea(LangBundle.message("text.area.packaging.error.no.information")).apply {
    isEditable = false
  }

  private val myCommandVisible = AtomicBooleanProperty(false)
  private val myCommand: JTextPane = JTextPane().apply {
    text = LangBundle.message("text.area.packaging.error.solution.none")
    isEditable = false
  }

  private val myMessageVisible = AtomicBooleanProperty(false)
  private val myMessageIcon: JBLabel = JBLabel()
  private val myMessage: JTextPane = JTextPane().apply {
    text = LangBundle.message("text.area.packaging.error.unknown.reason")
    isEditable = false
  }

  private val mySolutionVisible = AtomicBooleanProperty(false)
  private val mySolution: JTextPane = JTextPane().apply {
    text = LangBundle.message("text.area.packaging.error.solution.none")
    isEditable = false
  }

  private val myCommandOutputVisible = AtomicBooleanProperty(false)
  private val myCommandOutput: JTextArea = JTextArea(LangBundle.message("text.area.packaging.error.no.output")).apply {
    isEditable = false
  }

  init {
    init()
    setTitle(title)

    val message = errorDescription.message
    val command = errorDescription.command
    val output = errorDescription.output
    val solution = errorDescription.solution

    val extendedInfo = command != null || output != null || solution != null

    myDetailsVisible.set(!extendedInfo)
    myMessageVisible.set(extendedInfo)
    myCommandVisible.set(command != null)
    myCommandOutputVisible.set(output != null)
    mySolutionVisible.set(solution != null)

    if (extendedInfo) {
      myMessage.text = message
      myMessageIcon.icon = Messages.getErrorIcon()
    }
    else {
      myDetails.text = message
      myDetailsLabel.icon = Messages.getErrorIcon()
    }

    if (command != null) {
      myCommand.text = command
    }
    if (output != null) {
      myCommandOutput.text = output
    }
    if (solution != null) {
      mySolution.text = solution
    }
  }

  override fun getDimensionServiceKey() = this::class.java.name + ".DimensionServiceKey"

  override fun createCenterPanel() = panel { //Details
    rowsRange {
      row { cell(myDetailsLabel) }
      row { scrollCell(myDetails).resizableColumn().align(Align.FILL) }.resizableRow()
    }.visibleIf(myDetailsVisible)

    //Executed command
    rowsRange {
      row { label(LangBundle.message("label.packaging.executed.command")) }
      row { scrollCell(myCommand).resizableColumn().align(Align.FILL) }.resizableRow()
    }.visibleIf(myCommandVisible)

    // Error occurred
    rowsRange {
      row { label(LangBundle.message("label.packaging.error.occurred")) }
      row {
        cell(myMessageIcon)
        scrollCell(myMessage).resizableColumn().align(Align.FILL)
      }.resizableRow()
    }.visibleIf(myMessageVisible)

    // Proposed solution
    rowsRange {
      row { label(LangBundle.message("label.packaging.proposed.solution")) }
      row { scrollCell(mySolution).resizableColumn().align(Align.FILL) }.resizableRow()
    }.visibleIf(mySolutionVisible)

    // Command output
    rowsRange {
      row { label(LangBundle.message("label.packaging.command.output")) }
      row { scrollCell(myCommandOutput).resizableColumn().align(Align.FILL) }.resizableRow()
    }.visibleIf(myCommandOutputVisible)
  }.apply {
    preferredSize = JBUI.size(600, 400)
  }
}
