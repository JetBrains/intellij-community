// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HintHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

private val logger = fileLogger()

@Internal
interface ClientLookupManager {
  companion object {
    fun getInstance(session: ClientProjectSession): ClientLookupManager? = session.serviceOrNull()

    fun getCurrentInstance(project: Project): ClientLookupManager = project.currentSession.service()
  }

  fun getActiveLookup(): LookupEx?
  fun hideActiveLookup()
  @RequiresEdt
  fun createLookup(editor: Editor, items: Array<LookupElement>, prefix: String, arranger: LookupArranger): LookupImpl
  fun clear()

}

@Internal
abstract class ClientLookupManagerBase(val session: ClientProjectSession) : ClientLookupManager {

  private var myActiveLookup: LookupImpl? = null

  override fun getActiveLookup(): LookupEx? {
    val activeLookup = myActiveLookup
    if (activeLookup != null && activeLookup.isLookupDisposed) {
      val lookup: LookupImpl = activeLookup
      myActiveLookup = null
      lookup.checkValid()
    }

    return activeLookup
  }

  override fun hideActiveLookup() {
    val lookup = myActiveLookup
    if (lookup != null) {
      lookup.checkValid()
      lookup.hide()
      logger.assertTrue(lookup.isLookupDisposed, "Should be disposed")
    }
  }

  @RequiresEdt
  override fun createLookup(
    editor: Editor,
    items: Array<LookupElement>,
    prefix: String,
    arranger: LookupArranger,
  ): LookupImpl {
    hideActiveLookup()
    var lookup = LOOKUP_PROVIDER_EP.extensionList.firstNotNullOfOrNull { it.createLookup(editor, arranger, session) }
    if (lookup == null) {
      lookup = createLookup(editor, arranger, session)
    }
    LOOKUP_CUSTOMIZATION_EP.extensionList.forEach { ex ->
      ex.customizeLookup(lookup)
    }
    Disposer.register(lookup) {
      myActiveLookup = null
      (LookupManagerImpl.getInstance(session.project) as LookupManagerImpl).fireActiveLookupChanged(lookup, null)
    }

    if (items.isNotEmpty()) {
      val matcher = CamelHumpMatcher(prefix)
      for (item in items) {
        lookup.addItem(item, matcher)
      }
      lookup.refreshUi(true, true)
    }

    myActiveLookup = lookup
    (LookupManagerImpl.getInstance(session.project) as LookupManagerImpl).fireActiveLookupChanged(null, lookup)
    return lookup
  }

  override fun clear() {
    myActiveLookup?.hide()
    myActiveLookup = null
  }
  protected abstract fun createLookup(editor: Editor, arranger: LookupArranger, session: ClientProjectSession): LookupImpl
}

@ApiStatus.Experimental
@Internal
val LOOKUP_CUSTOMIZATION_EP: ExtensionPointName<LookupCustomizer> = ExtensionPointName("com.intellij.lookup.customizer")

@ApiStatus.Experimental
@Internal
val LOOKUP_PROVIDER_EP: ExtensionPointName<LookupProvider> = ExtensionPointName.create("com.intellij.lookup.provider")

/**
 * Represents a customization mechanism for a lookup interface. Classes implementing this
 * interface can provide additional configurations or modifications to a given `LookupImpl` object.
 *
 * This is intended to be used internally within the system and not exposed for external use.
 * Use [com.intellij.codeInsight.lookup.LookupManagerListener]
 */
@ApiStatus.Experimental
@Internal
interface LookupCustomizer {
  fun customizeLookup(lookupImpl: LookupImpl)
}

/**
 * Allows overriding LookupImpl for certain editors.
 * Can be used to customize behavior and look of the lookup, for example,
 * to display the lookup not as a hint, but using some other UI,
 * like embed it inside some window, change its position or background color.
 */
@ApiStatus.Experimental
@Internal
interface LookupProvider {
  fun createLookup(editor: Editor, arranger: LookupArranger, session: ClientProjectSession): LookupImpl?
}

@Internal
class LocalClientLookupManager(session: ClientProjectSession) : ClientLookupManagerBase(session) {
  override fun createLookup(editor: Editor, arranger: LookupArranger, session: ClientProjectSession): LookupImpl {
    return LookupImpl(session, editor, arranger)
  }
}

@Internal
class GuestLookupManager(session: ClientProjectSession) : ClientLookupManagerBase(session) {
  override fun createLookup(editor: Editor, arranger: LookupArranger, session: ClientProjectSession): LookupImpl {
    return GuestLookupImpl(session, editor, arranger)
  }

  class GuestLookupImpl(session: ClientProjectSession, editor: Editor, arranger: LookupArranger) : LookupImpl(session, editor, arranger) {
    override fun doShowLookup(): Boolean {
      return true
    }

    override fun show(parentComponent: JComponent, x: Int, y: Int, focusBackComponent: JComponent?, hintHint: HintHint) {
    }

    override fun isAvailableToUser(): Boolean {
      return myShown
    }
  }
}
