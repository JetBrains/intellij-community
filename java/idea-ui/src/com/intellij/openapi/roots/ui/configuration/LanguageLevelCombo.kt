// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.DefaultComboBoxModel

abstract class LanguageLevelCombo(defaultItem: @Nls String?) : ComboBox<LanguageLevelCombo.ComboItems>() {

  sealed class ComboItems: GroupedComboBoxRenderer.Item {
    data class Default(val label: @Nls String) : ComboItems() {
      override fun getText(): String = label
    }
    data class Level(val level: LanguageLevel) : ComboItems() {
      override fun getText(): String = level.presentableText
    }
    data class Separator(val label: @NlsContexts.Separator String) : ComboItems() {
      override fun isSeparator(): Boolean = true
      override fun getSeparatorText(): String = label
    }
  }

  init {
    val items = mutableListOf<ComboItems>()
    items.add(ComboItems.Default(defaultItem ?: ""))
    items.add(ComboItems.Separator(JavaUiBundle.message("language.level.combo.lts.versions")))
    for (level in LTS) {
      items.add(ComboItems.Level(level))
    }
    items.add(ComboItems.Separator(JavaUiBundle.message("language.level.combo.other.versions")))
    val highestPreviewLevel = LanguageLevel.HIGHEST.previewLevel
    val highestWithPreview = highestPreviewLevel ?: LanguageLevel.HIGHEST
    LanguageLevel.values()
      .sortedBy { it.toJavaVersion().feature }
      .filter { level: LanguageLevel -> level <= highestWithPreview && (level.isPreview || !ArrayUtil.contains(level, *LTS)) }
      .forEach { level: LanguageLevel -> items.add(ComboItems.Level(level)) }
    items.add(ComboItems.Separator(JavaUiBundle.message("language.level.combo.experimental.versions")))
    for (level in LanguageLevel.values()) {
      if (level > highestWithPreview) {
        items.add(ComboItems.Level(level))
      }
    }
    isSwingPopup = false
    model = DefaultComboBoxModel(items.toTypedArray())
    renderer = GroupedComboBoxRenderer(this)
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
        is ComboItems.Level -> item.level
        is ComboItems.Default -> defaultLevel
        else -> null
      }
    }
  val isDefault: Boolean
    get() = selectedItem is ComboItems.Default

  override fun setSelectedItem(anObject: Any?) {
    val levelToSelect: @NonNls Any = anObject ?: "default"
    val entryForLevel = getEntryForLevel(levelToSelect)
    if (entryForLevel != null) super.setSelectedItem(entryForLevel)
    if (entryForLevel is ComboItems.Level) checkAcceptedLevel(entryForLevel.level)
  }

  private fun getEntryForLevel(levelToSelect: Any?): ComboItems? {
    for (i in 0 until itemCount) {
      val entry = getItemAt(i)
      if (levelToSelect == entry) return entry
      when (entry) {
        is ComboItems.Level -> if (levelToSelect == entry.level) return entry
        is ComboItems.Default -> if (levelToSelect is String) return entry
        else -> {}
      }
    }
    return null
  }

  companion object {
    private val LTS = arrayOf(LanguageLevel.JDK_17, LanguageLevel.JDK_11, LanguageLevel.JDK_1_8)
  }
}