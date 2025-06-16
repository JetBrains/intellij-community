// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.JavaRelease
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.DefaultComboBoxModel

@OptIn(ExperimentalStdlibApi::class)
abstract class LanguageLevelCombo @JvmOverloads constructor(
  defaultItem: @Nls String?,
  // It could be an open method but since we use it in constructor we have a leaking this problem
  levelFilter: (LanguageLevel) -> Boolean = {true}
) : ComboBox<Any>() {

  init {
    val items = mutableListOf<Any>()
    items.add(defaultItem ?: "")

    val highestLanguageLevel = JavaRelease.getHighest()
    val highestWithPreview = highestLanguageLevel.getPreviewLevel() ?: highestLanguageLevel

    fun MutableList<Any>.filterAndAdd(levels: Collection<LanguageLevel>) = levels.filter(levelFilter).also { addAll(it) }

    val regularItems = items.filterAndAdd(buildList {
      LanguageLevel.entries
        .sortedBy { -it.feature() }
        .filter { level: LanguageLevel -> level <= highestWithPreview && !level.isUnsupported }
        .forEach { level: LanguageLevel -> add(level) }
    })

    val experimentalItems = items.filterAndAdd(buildList {
      for (level in LanguageLevel.entries.reversed()) {
        if (level > highestWithPreview && !level.isUnsupported) {
          add(level)
        }
      }
    })
    
    val unsupportedItems = items.filterAndAdd(buildList {
      for (level in LanguageLevel.entries.reversed()) {
        if (level.isUnsupported) {
          add(level)
        }
      }
    })

    val separatorsMap: Map<Any?, ListSeparator> = listOf(
      regularItems.firstOrNull() to ListSeparator(JavaUiBundle.message("language.level.combo.supported.versions")),
      experimentalItems.firstOrNull() to ListSeparator(JavaUiBundle.message("language.level.combo.experimental.versions")),
      unsupportedItems.firstOrNull() to ListSeparator(JavaUiBundle.message("language.level.combo.unsupported.versions")),
    ).filter { it.first != null }.toMap()

    isSwingPopup = false
    model = DefaultComboBoxModel(items.toTypedArray())
    renderer = listCellRenderer("") {
      val value = value
      text(when (value) {
             is String -> value
             is LanguageLevel -> value.presentableText
             else -> ""
           }) {
        if (value is LanguageLevel && value.isUnsupported) {
          attributes = SimpleTextAttributes.ERROR_ATTRIBUTES
        } else if (value is LanguageLevel && LTS.contains(value)) {
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.derive(SimpleTextAttributes.STYLE_BOLD, null, null, null)
        }
      }
      separatorsMap[value]?.let {
        separator { text = it.text }
      }
    }
  }

  private fun checkAcceptedLevel(selectedLevel: LanguageLevel?) {
    if (selectedLevel == null) return
    hidePopup()
    val level = AcceptedLanguageLevelsSettings.checkAccepted(this, selectedLevel)
    if (level == null) {
      selectedItem = AcceptedLanguageLevelsSettings.getHighestAcceptedLevel()
    }
  }

  fun reset(project: Project) {
    val sdk = ProjectRootManagerEx.getInstanceEx(project).projectSdk
    sdkUpdated(sdk, project.isDefault)
    val extension = LanguageLevelProjectExtension.getInstance(project)
    selectedItem = when {
      extension.isDefault -> "default"
      else -> extension.languageLevel
    }
  }

  protected abstract val defaultLevel: LanguageLevel?

  fun sdkUpdated(sdk: Sdk?, isDefaultProject: Boolean) {
    var newLevel: LanguageLevel? = null
    if (sdk != null) {
      val version = JavaSdk.getInstance().getVersion(sdk)
      if (version != null) {
        newLevel = version.maxLanguageLevel
      }
    }
    updateDefaultLevel(newLevel, isDefaultProject)
    if (isDefault) {
      checkAcceptedLevel(newLevel)
    }
  }

  private fun updateDefaultLevel(newLevel: LanguageLevel?, isDefaultProject: Boolean) {
    if (newLevel == null && !isDefaultProject) {
      if (isDefault) {
        selectedItem = defaultLevel
      }
    }
    repaint()
  }

  val selectedLevel: LanguageLevel?
    get() {
      return when (val item = selectedItem) {
        is LanguageLevel -> item
        is String -> defaultLevel
        else -> null
      }
    }
  val isDefault: Boolean
    get() = selectedItem is String

  override fun setSelectedItem(anObject: Any?) {
    val levelToSelect: @NonNls Any = anObject ?: "default"
    val entryForLevel = getEntryForLevel(levelToSelect)
    if (entryForLevel != null) super.setSelectedItem(entryForLevel)
    if (entryForLevel is LanguageLevel) checkAcceptedLevel(entryForLevel)
  }

  private fun getEntryForLevel(levelToSelect: Any?): Any? {
    for (i in 0 until itemCount) {
      val entry = getItemAt(i)
      if (levelToSelect == entry) return entry
      when (entry) {
        is LanguageLevel -> if (levelToSelect == entry) return entry
        is String -> if (levelToSelect is String) return entry
        else -> {}
      }
    }
    return null
  }

  companion object {
    private val LTS = arrayOf(LanguageLevel.JDK_21, LanguageLevel.JDK_17, LanguageLevel.JDK_11, LanguageLevel.JDK_1_8)
  }
}