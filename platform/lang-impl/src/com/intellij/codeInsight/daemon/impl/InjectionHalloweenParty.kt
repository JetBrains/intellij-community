// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope

private const val REGISTRY_KEY = "editor.injection.halloween.party"
private val FONT_FAMILIES = Key.create<FontFamilies>("INJECTION_HALLOWEEN_PARTY_FONT_FAMILIES")
private val HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("INJECTION_HALLOWEEN_PARTY_HIGHLIGHTERS")

/**
 * The font families that draw symbols instead of letters.
 */
private val SYMBOL_FAMILY = Regex("(?i)dingbat|wingding|webding|symbol|emoji|icon|ornament|braille|music")

/**
 * Returns true when [InjectionHalloweenParty] is on.
 */
internal fun isInjectionHalloweenPartyOn(): Boolean {
  return RegistryManager.getInstance().`is`(REGISTRY_KEY)
}

/**
 * Turns [InjectionHalloweenParty] on or off.
 * The registry keeps the value after a restart of the IDE.
 */
internal fun setInjectionHalloweenPartyOn(on: Boolean) {
  RegistryManager.getInstance().get(REGISTRY_KEY).setValue(on)
}

/**
 * The Injection Halloween Party is the internal demo for IJPL-259206.
 *
 * When the party is on, gives every injected document one random installed font family after each highlighting pass.
 * Each host range of the document gets an editor highlighter that sets only the font families.
 * [InjectionHalloweenPartyAction] turns the party on and off.
 *
 * The service exists only after the party turns on in the project for the first time.
 * From then on, it listens to the daemon until the project closes.
 */
@Service(Service.Level.PROJECT)
internal class InjectionHalloweenParty(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : DaemonCodeAnalyzer.DaemonListener {
  init {
    project.messageBus.connect(coroutineScope).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, this)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (!isInjectionHalloweenPartyOn()) {
      return
    }
    val editors = fileEditors.filterIsInstance<TextEditor>().map { it.editor }
    ApplicationManager.getApplication().invokeLater({ editors.forEach(::updateHighlighters) }, project.disposed)
  }

  /**
   * Keeps the highlighters whose range and font families did not change, removes the others, and adds the missing ones.
   */
  @RequiresEdt
  private fun updateHighlighters(editor: Editor) {
    if (editor.isDisposed || !isInjectionHalloweenPartyOn()) {
      return
    }
    val document = editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    val injectedDocuments = InjectedLanguageManager.getInstance(project)
      .getCachedInjectedDocumentsInRange(psiFile, TextRange(0, document.textLength))
    val missing = LinkedHashSet<Pair<TextRange, FontFamilies>>()
    for (injectedDocument in injectedDocuments) {
      val fontFamilies = getFontFamilies(injectedDocument) ?: continue
      for (hostRange in injectedDocument.hostRanges) {
        if (hostRange.startOffset < hostRange.endOffset) {
          missing.add(TextRange.create(hostRange) to fontFamilies)
        }
      }
    }
    val highlighters = ArrayList<RangeHighlighter>()
    for (highlighter in editor.getUserData(HIGHLIGHTERS).orEmpty()) {
      val fontFamilies = highlighter.getTextAttributes(null)?.fontFamilies
      if (highlighter.isValid && fontFamilies != null && missing.remove(highlighter.textRange to fontFamilies)) {
        highlighters.add(highlighter)
      } else {
        highlighter.dispose()
      }
    }
    for ((range, fontFamilies) in missing) {
      val attributes = TextAttributes()
      attributes.fontFamilies = fontFamilies
      val highlighter = editor.markupModel.addRangeHighlighter(
        range.startOffset,
        range.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX + 1,
        attributes,
        HighlighterTargetArea.EXACT_RANGE,
      )
      highlighter.setGreedyToLeft(true)
      highlighter.setGreedyToRight(true)
      highlighters.add(highlighter)
    }
    editor.putUserData(HIGHLIGHTERS, highlighters)
  }

  /**
   * Returns one random installed font family for the injected document, the same on every highlighting pass.
   */
  private fun getFontFamilies(injectedDocument: DocumentWindow): FontFamilies? {
    injectedDocument.getUserData(FONT_FAMILIES)?.let {
      return it
    }
    val family = FontFamilyService.getAvailableFamilies().filterNot { SYMBOL_FAMILY.containsMatchIn(it) }.randomOrNull() ?: return null
    val fontFamilies = FontFamilies.of(family)
    injectedDocument.putUserData(FONT_FAMILIES, fontFamilies)
    return fontFamilies
  }
}

/**
 * Starts the party in a project that opens while the party is on.
 * The restart of the highlighting makes sure that the party gets the first highlighting pass.
 */
internal class InjectionHalloweenPartyActivity : ProjectActivity {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (RegistryManager.getInstanceAsync().`is`(REGISTRY_KEY)) {
      project.serviceAsync<InjectionHalloweenParty>()
      project.serviceAsync<DaemonCodeAnalyzer>().restart("the project opens with the injection Halloween party on")
    }
  }
}

/**
 * Applies a change of the registry key at once, also a change from the Registry dialog.
 * When the party turns on, the listener starts the party in the open projects and restarts their highlighting.
 * When the party turns off, the listener removes the highlighters of the party from all editors.
 */
private class InjectionHalloweenPartyRegistryListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key != REGISTRY_KEY) {
      return
    }
    if (value.asBoolean()) {
      for (project in ProjectManager.getInstance().openProjects) {
        project.service<InjectionHalloweenParty>()
        DaemonCodeAnalyzer.getInstance(project).restart("the injection Halloween party turns on")
      }
    } else {
      ApplicationManager.getApplication().invokeLater(::removeAllHighlighters)
    }
  }
}

/**
 * Removes the highlighters of the party from all editors.
 */
@RequiresEdt
private fun removeAllHighlighters() {
  for (editor in EditorFactory.getInstance().allEditors) {
    editor.getUserData(HIGHLIGHTERS)?.forEach(RangeHighlighter::dispose)
    editor.putUserData(HIGHLIGHTERS, null)
  }
}
