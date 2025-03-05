// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.chain

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.declarative.InlayHintsCustomSettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.event.ChangeListener

@ApiStatus.Internal
abstract class AbstractDeclarativeCallChainCustomSettingsProvider(
  private val defaultChainLength: Int
) : InlayHintsCustomSettingsProvider<Int> {

  private var uniqueTypeCount = defaultChainLength
  private val uniqueTypeCountSpinner: JBIntSpinner by lazy {
    val spinner = JBIntSpinner(uniqueTypeCount, 0, 10)
    spinner.addChangeListener(ChangeListener {
      uniqueTypeCount = spinner.number
    })
    spinner
  }

  private val component by lazy {
    panel {
      row(CodeInsightBundle.message("inlay.hints.chain.minimal.unique.type.count.to.show.hints")) {
        cell(uniqueTypeCountSpinner)
      }
    }.also {
      it.border = JBUI.Borders.empty(5)
    }
  }

  override fun createComponent(project: Project, language: Language): JComponent {
    val callChainSettings = DeclarativeCallChainInlaySettings.getInstance(project)
    val chainLength = callChainSettings.getLanguageCallChainLength(language)
    uniqueTypeCount = chainLength ?: defaultChainLength
    invokeLater {
      uniqueTypeCountSpinner.number = uniqueTypeCount
    }
    return component
  }

  override fun isDifferentFrom(project: Project, settings: Int): Boolean {
    return settings != uniqueTypeCount
  }

  override fun getSettingsCopy(): Int {
    return uniqueTypeCount
  }

  override fun persistSettings(project: Project, settings: Int, language: Language) {
    val callChainSettings = DeclarativeCallChainInlaySettings.getInstance(project)
    callChainSettings.setLanguageCallChainLength(language, uniqueTypeCount, defaultChainLength)
  }

  override fun putSettings(project: Project, settings: Int, language: Language) {
    uniqueTypeCount = settings
    invokeLater {
      uniqueTypeCountSpinner.number = settings
    }
  }
}