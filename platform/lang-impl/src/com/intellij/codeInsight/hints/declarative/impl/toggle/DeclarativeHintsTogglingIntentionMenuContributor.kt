// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.toggle

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import org.jetbrains.annotations.Nls

class DeclarativeHintsTogglingIntentionMenuContributor : IntentionMenuContributor, DumbAware {
  override fun collectActions(hostEditor: Editor,
                              hostFile: PsiFile,
                              intentions: ShowIntentionsPass.IntentionsInfo,
                              passIdToShowIntentionsFor: Int,
                              offset: Int) {
    val context = Context.gather(hostFile.project, hostEditor, hostFile) ?: return
    for (providerInfo in context.providersToToggle) {
      val action = DeclarativeHintsTogglingIntention(providerInfo.providerId, providerInfo.providerName, providerInfo.providerEnabled)
      val descriptor = HighlightInfo.IntentionActionDescriptor(action, mutableListOf(), null, null, null, null, HighlightSeverity.INFORMATION)
      intentions.intentionsToShow.add(descriptor)
    }
    val settings = DeclarativeInlayHintsSettings.getInstance()
    for (providersWithOption in context.providersWithOptions) {
      val providerId = providersWithOption.providerId
      val providerInfo = InlayHintsProviderFactory.getProviderInfo(hostFile.language, providerId) ?: continue
      val optionId = providersWithOption.optionId
      val optionInfo = providerInfo.options.find { it.id == optionId } ?: continue
      val optionEnabled = settings.isOptionEnabled(optionId, providerId) ?: optionInfo.isEnabledByDefault
      val providerEnabled = providersWithOption.providerEnabled
      val mode = if (providerEnabled) {
        if (optionEnabled) {
          DeclarativeHintsTogglingOptionIntention.Mode.DisableOption
        }
        else {
          DeclarativeHintsTogglingOptionIntention.Mode.EnableOption
        }
      }
      else {
        DeclarativeHintsTogglingOptionIntention.Mode.EnableProviderAndOption
      }
      val action = DeclarativeHintsTogglingOptionIntention(optionId, providerId, providersWithOption.providerName, optionInfo.name, mode)
      val descriptor = HighlightInfo.IntentionActionDescriptor(action, mutableListOf(), null, null, null, null, HighlightSeverity.INFORMATION)
      intentions.intentionsToShow.add(descriptor)
    }
  }

  private class DummyInlayTreeSink : InlayTreeSink {
    var attemptedToAddUnderOptions: MutableSet<String> = HashSet()
    var attemptedToAddWithoutOptions = false

    private val currentOptions = HashSet<String>()

    override fun addPresentation(position: InlayPosition,
                                 payloads: List<InlayPayload>?,
                                 tooltip: String?,
                                 hintFormat: HintFormat,
                                 builder: PresentationTreeBuilder.() -> Unit) {
      if (currentOptions.isEmpty()) {
        attemptedToAddWithoutOptions = true
      } else {
        attemptedToAddUnderOptions.addAll(currentOptions)
      }
    }

    override fun whenOptionEnabled(optionId: String, block: () -> Unit) {
      currentOptions.add(optionId)
      try {
        block()
      } finally {
        currentOptions.remove(optionId)
      }
    }
  }

  private data class ProviderWithOptionInfo(val providerId: String, val optionId: String, val providerName: @Nls String, val providerEnabled: Boolean)

  private data class ProviderInfo(val providerId: String, val providerName: @Nls String, val providerEnabled: Boolean)

  private data class SharedCollectorInfo(val collector: SharedBypassCollector, val sink: DummyInlayTreeSink, val providerId: String, val providerName: String, val enabled: Boolean)

  private data class OwnCollectorInfo(val collector: OwnBypassCollector, val providerId: String, val providerName: String, val enabled: Boolean)

  private class Context(
    val providersWithOptions: Set<ProviderWithOptionInfo>,
    val providersToToggle: Set<ProviderInfo>
  )  {
    companion object {
      fun gather(project: Project, editor: Editor, file: PsiFile) : Context? {
        val providers = DeclarativeInlayHintsPassFactory.getSuitableToFileProviders(file)
        if (providers.isEmpty()) return null
        val ownBypassCollectors = ArrayList<OwnCollectorInfo>()
        val sharedBypassCollectors = ArrayList<SharedCollectorInfo>()
        val settings = DeclarativeInlayHintsSettings.getInstance()
        for (provider in providers) {
          val collector = provider.provider.createCollector(file, editor)
          if (collector == null) continue
          val providerId = provider.providerId
          val enabled = settings.isProviderEnabled(providerId) ?: provider.isEnabledByDefault
          when (collector) {
            is OwnBypassCollector -> {
              ownBypassCollectors.add(OwnCollectorInfo(collector, providerId, provider.providerName, enabled))
            }
            is SharedBypassCollector -> {
              sharedBypassCollectors.add(SharedCollectorInfo(collector, DummyInlayTreeSink(), providerId, provider.providerName, enabled))
            }
          }
        }
        val providersToToggle = HashSet<ProviderInfo>()
        val providersWithOptionsToToggle = HashSet<ProviderWithOptionInfo>()
        if (sharedBypassCollectors.isNotEmpty()) {
          val offset = editor.caretModel.offset
          val element = file.findElementAt(offset)
          if (element != null) {
            for (parent in element.parents(true)) {
              for ((collector, sink, providerId, providerName, providerEnabled) in sharedBypassCollectors) {
                collector.collectFromElementForActions(parent, sink)
                if (sink.attemptedToAddWithoutOptions) {
                  providersToToggle.add(ProviderInfo(providerId, providerName, providerEnabled))
                }
                if (sink.attemptedToAddUnderOptions.isNotEmpty()) {
                  for (optionId in sink.attemptedToAddUnderOptions) {
                    providersWithOptionsToToggle.add(ProviderWithOptionInfo(providerId, optionId, providerName, providerEnabled))
                  }
                }
              }
            }
          }
        }
        if (ownBypassCollectors.isNotEmpty()) {
          for ((collector, providerId, providerName, providerEnabled) in ownBypassCollectors) {
            if (collector.shouldSuggestToggling(project, editor, file)) {
              providersToToggle.add(ProviderInfo(providerId, providerName, providerEnabled))
            }
            val optionsToToggle = collector.getOptionsToToggle(project, editor, file)
            for (optionId in optionsToToggle) {
              providersWithOptionsToToggle.add(ProviderWithOptionInfo(providerId, optionId, providerName, providerEnabled))
            }
          }
        }
        if (providersToToggle.isEmpty() && providersWithOptionsToToggle.isEmpty()) return null

        return Context(providersWithOptionsToToggle, providersToToggle)
      }
    }
  }
}