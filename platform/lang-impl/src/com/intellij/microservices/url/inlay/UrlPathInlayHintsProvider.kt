// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.lang.Language
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_INLAY_ACTIONS_EVENT
import com.intellij.microservices.url.references.forbidExpensiveUrlContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.semantic.SemService
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

internal class UrlPathInlayHintsProvider(private val languagesProvider: UrlPathInlayLanguagesProvider) : InlayHintsProvider<NoSettings> {
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    if (DumbService.isDumb(file.project) || file.project.isDefault) return null

    val semService = SemService.getSemService(file.project)

    return object : FactoryInlayHintsCollector(editor) {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        forbidExpensiveUrlContext {
          val hintSemProvider = inlaysInElement(languagesProvider, element, semService).selectProvidersFromGroups()
          val hints = hintSemProvider.flatMap { it.inlayHints.asSequence() }

          for (hint in hints) {
            val actions = hint.getAvailableActions(file)
            val presentation = with(factory) {
              referenceOnHover(
                withTooltip(getTooltip(actions), hint.getPresentation(editor, factory))
              ) { event, _ ->
                when {
                  actions.isEmpty() -> HintManager.getInstance()
                    .showInformationHint(editor, MicroservicesBundle.message("microservices.inlay.no.actions.message"))
                  actions.size == 1 -> actions[0].actionPerformed(file, editor, hint, event)
                  else -> showPopupForActions(editor, file, event, actions, hint)
                }
              }
            }

            when (hint.style) {
              UrlPathInlayHint.Style.BLOCK -> sink.addBlockElement(hint.offset, false, true, hint.priority, presentation)
              UrlPathInlayHint.Style.INLINE -> sink.addInlineElement(hint.offset, false, presentation, false)
            }
          }
        }

        return true
      }
    }
  }

  private fun showPopupForActions(editor: Editor,
                                  file: PsiFile,
                                  event: MouseEvent,
                                  actions: List<UrlPathInlayAction>,
                                  hint: UrlPathInlayHint) {
    URL_INLAY_ACTIONS_EVENT.log(file.project)

    JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<UrlPathInlayAction>(
      MicroservicesBundle.message("microservices.inlay.actions.title"),
      actions
    ) {
      override fun getIconFor(value: UrlPathInlayAction): Icon {
        return value.icon
      }

      override fun getTextFor(value: UrlPathInlayAction): String {
        return value.name
      }

      override fun onChosen(selectedValue: UrlPathInlayAction, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep { selectedValue.actionPerformed(file, editor, hint, event) }
      }
    }).show(RelativePoint(event))
  }

  override fun createSettings(): NoSettings = NoSettings()

  override val name: String
    get() = MicroservicesBundle.message("microservices.inlay.provider.name")

  override val key: SettingsKey<NoSettings>
    get() = KEY

  override val group: InlayGroup
    get() = InlayGroup.URL_PATH_GROUP

  override val description: String
    get() = MicroservicesBundle.message("inlay.microservices.url.path.inlay.hints.description")

  override val previewText: String?
    get() = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent = panel {}
  }

  @Nls
  private fun getTooltip(actions: List<UrlPathInlayAction>): String {
    if (actions.size == 1) {
      return MicroservicesBundle.message("microservices.inlay.one.action.tooltip", actions[0].name)
    }
    else {
      return MicroservicesBundle.message("microservices.inlay.tooltip")
    }
  }

  companion object {
    internal val EP_NAME: ExtensionPointName<UrlPathInlayAction> = ExtensionPointName.Companion.create("com.intellij.microservices.urlInlayAction")

    private val KEY = SettingsKey<NoSettings>("microservices.url.path.inlay.hints")

    internal fun inlaysInElement(
      urlPathInlayLanguagesProvider: UrlPathInlayLanguagesProvider,
      element: PsiElement,
      semService: SemService
    ): Sequence<UrlPathInlayHintsProviderSemElement> {
      return urlPathInlayLanguagesProvider.getPotentialElementsWithHintsProviders(element).asSequence()
        .flatMap {
          // there is no to little sense in caching SEM holder in the most of PSI elements, let's not do that
          semService.getSemElementsNoCache(UrlPathInlayHintsProviderSemElement.INLAY_HINT_SEM_KEY, it).asSequence()
        }
    }

    internal fun setUrlPathInlaysEnabledForLanguage(language: Language, enabled: Boolean) {
      InlayHintsSettings.Companion.instance().changeHintTypeStatus(KEY, language, enabled)
    }

    @ApiStatus.Internal
    fun isUrlPathInlaysEnabledForLanguage(language: Language): Boolean {
      return InlayHintsSettings.Companion.instance().hintsShouldBeShown(KEY, language)
    }

    internal fun openUrlPathInlaySettings(project: Project, language: Language) {
      showInlaySettings(project, language) {
        KEY.id == it.id
      }
    }
  }
}

internal class UrlPathInlayActionServiceImpl : UrlPathInlayActionService {
  override fun getAvailableActions(file: PsiFile, hint: UrlPathInlayHint): List<UrlPathInlayAction> {
    return UrlPathInlayHintsProvider.EP_NAME.extensionList
      .filter { it.isAvailable(file, hint) }
  }

  override fun buildPresentation(editor: Editor, factory: InlayPresentationFactory): InlayPresentation {
    return with(ScaleAwarePresentationFactory(editor, factory as PresentationFactory)) {
      urlInlayPresentation()
    }
  }
}