// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

private const val EXTENSION_POINT_NAME = "com.intellij.codeInsight.inlayProvider"

enum class InlayGroup(val key: String) {
  CODE_VISION_GROUP("settings.hints.group.code.vision"),
  PARAMETERS_GROUP("settings.hints.group.parameters"),
  TYPES_GROUP("settings.hints.group.types"),
  VALUES_GROUP("settings.hints.group.values"),
  ANNOTATIONS_GROUP("settings.hints.group.annotations"),
  METHOD_CHAINS_GROUP("settings.hints.group.method.chains"),
  LAMBDAS_GROUP("settings.hints.group.lambdas"),
  CODE_AUTHOR_GROUP("settings.hints.group.code.author"),
  URL_PATH_GROUP("settings.hints.group.url.path"),
  OTHER_GROUP("settings.hints.group.other");

  override fun toString(): @Nls String {
    return ApplicationBundle.message(key)
  }}

object InlayHintsProviderExtension : LanguageExtension<InlayHintsProvider<*>>(EXTENSION_POINT_NAME) {
  private fun findLanguagesWithHintsSupport(): List<Language> {
    val extensionPointName = inlayProviderName
    return extensionPointName.extensionList.map { it.language }
      .toSet()
      .mapNotNull { Language.findLanguageByID(it) }
  }

  fun findProviders() : List<ProviderInfo<*>> {
    return findLanguagesWithHintsSupport().flatMap { language ->
      InlayHintsProviderExtension.allForLanguage(language).map { ProviderInfo(language, it) }
    }
  }

  val inlayProviderName = ExtensionPointName<LanguageExtensionPoint<InlayHintsProvider<*>>>(EXTENSION_POINT_NAME)
}

/**
 * Provider of inlay hints for single language. If you need to create hints for multiple languages, please use [InlayHintsProviderFactory].
 * Both block and inline hints collection are supported.
 * Block hints draws between lines of code text. Inline ones are placed on the code text line (like parameter hints)
 * @param T settings type of this provider, if no settings required, please, use [NoSettings]
 * @see com.intellij.openapi.editor.InlayModel.addInlineElement
 * @see com.intellij.openapi.editor.InlayModel.addBlockElement
 *
 * To test it you may use InlayHintsProviderTestCase.
 * Mark as [com.intellij.openapi.project.DumbAware] to enable it in dumb mode.
 */
interface InlayHintsProvider<T : Any> {
  /**
   * If this method is called, provider is enabled for this file
   * Warning! Your collector should not use any settings besides [settings]
   */
  fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector?

  /**
   * Returns quick collector of placeholders.
   * Placeholders are shown on editor opening and stay until [getCollectorFor] collector hints are calculated.
   */
  @ApiStatus.Experimental
  @JvmDefault
  fun getPlaceholdersCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? = null

  /**
   * Settings must be plain java object, fields of these settings will be copied via serialization.
   * Must implement `equals` method, otherwise settings won't be able to track modification.
   * Returned object will be used to create configurable and collector.
   * It persists automatically.
   */
  fun createSettings(): T

  @get:Nls(capitalization = Nls.Capitalization.Sentence)

  /**
   * Name of this kind of hints. It will be used in settings and in context menu.
   * Please, do not use word "hints" to avoid duplication
   */
  val name: String

  @JvmDefault
  val group: InlayGroup get() = InlayGroup.OTHER_GROUP

  /**
   * Used for persisting settings.
   */
  val key: SettingsKey<T>

  @JvmDefault
  val description: String?
    get() {
      return getProperty("inlay." + key.id + ".description")
    }

  /**
   * Text, that will be used in the settings as a preview.
   */
  val previewText: String?

  /**
   * Creates configurable, that immediately applies changes from UI to [settings].
   */
  fun createConfigurable(settings: T): ImmediateConfigurable

  /**
   * Checks whether the language is accepted by the provider.
   */
  fun isLanguageSupported(language: Language): Boolean = true

  @JvmDefault
  fun createFile(project: Project, fileType: FileType, document: Document): PsiFile {
    val factory = PsiFileFactory.getInstance(project)
    return factory.createFileFromText("dummy", fileType, document.text)
  }

  @Nls
  @JvmDefault
  fun getProperty(key: String): String? = null

  @JvmDefault
  fun preparePreview(editor: Editor, file: PsiFile, settings: T) {
  }

  val isVisibleInSettings: Boolean
    get() = true
}

/**
 * The same as [UnnamedConfigurable], but not waiting for apply() to save settings.
 */
interface ImmediateConfigurable {
  /**
   * Creates component, which listen to its components and immediately updates state of settings object
   * This is required to make preview in settings works instantly
   * Note, that if you need to express only cases of this provider, you should use [cases] instead
   */
  fun createComponent(listener: ChangeListener): JComponent

  /**
   * Loads state from its configurable.
   */
  @JvmDefault
  fun reset() {}

  /**
   * Text, that will be used in settings for checkbox to enable/disable hints.
   */
  @JvmDefault
  val mainCheckboxText: String
    get() = "Show hints"

  @JvmDefault
  val cases : List<Case>
    get() = emptyList()

  class Case(
    @Nls val name: String,
    val id: String,
    private val loadFromSettings: () -> Boolean,
    private val onUserChanged: (Boolean) -> Unit,
    @NlsContexts.DetailedDescription
    val extendedDescription: String? = null
  ) {
    var value: Boolean
      get() = loadFromSettings()
      set(value) = onUserChanged(value)

    constructor(@Nls name: String, id: String, property: KMutableProperty0<Boolean>, @NlsContexts.DetailedDescription extendedDescription: String? = null) : this(
      name,
      id,
      { property.get() },
      {property.set(it)},
      extendedDescription
    )
  }
}



interface ChangeListener {
  /**
   * This method should be called on any change of corresponding settings.
   */
  fun settingsChanged()
}

/**
 * This class should be used if provider should not have settings. If you use e.g. [Unit] you will have annoying warning in logs.
 */
@Property(assertIfNoBindings = false)
class NoSettings {
  override fun equals(other: Any?): Boolean = other is NoSettings

  override fun hashCode(): Int {
    return javaClass.hashCode()
  }
}

/**
 * Similar to [com.intellij.openapi.util.Key], but it also requires language to be unique.
 * Allows type-safe access to settings of provider.
 */
@Suppress("unused")
data class SettingsKey<T>(val id: String) {
  fun getFullId(language: Language): String = language.id + "." + id
}

interface AbstractSettingsKey<T: Any> {
  fun getFullId(language: Language): String
}

data class InlayKey<T: Any, C: Any>(val id: String) : AbstractSettingsKey<T>, ContentKey<C> {
  override fun getFullId(language: Language): String = language.id + "." + id
}

/**
 * Allows type-safe access to content of the root presentation.
 */
interface ContentKey<C: Any>