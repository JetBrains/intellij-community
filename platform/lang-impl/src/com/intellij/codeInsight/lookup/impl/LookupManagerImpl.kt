// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.sessions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

@ApiStatus.Internal
open class LookupManagerImpl(private val myProject: Project) : LookupManager() {

  @Deprecated("Deprecated as the methods using the field")
  private val myPropertyChangeSupport = PropertyChangeSupport(this)

  init {
    val connection = myProject.messageBus.connect()
    connection.subscribe(EditorHintListener.TOPIC, object : EditorHintListener {
      override fun hintShown(editor: Editor, hint: LightweightHint, flags: Int, hintInfo: HintHint) {
        if (editor.project == myProject) {
          val lookup: Lookup? = activeLookup
          if (lookup != null && BitUtil.isSet(flags, HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE)) {
            lookup.addLookupListener(object : LookupListener {
              override fun currentItemChanged(event: LookupEvent) {
                hint.hide()
              }

              override fun itemSelected(event: LookupEvent) {
                hint.hide()
              }

              override fun lookupCanceled(event: LookupEvent) {
                hint.hide()
              }
            })
          }
        }
      }
    })

    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        hideActiveLookup()
      }

      override fun exitDumbMode() {
        hideActiveLookup()
      }
    })


    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        // Do not use ClientKind.ALL because it provides FRONTEND type of session that should be removed
        // and the container fails to resolve even nullable service of such kind of a session
        for (session in myProject.sessions(ClientKind.LOCAL) + myProject.sessions(ClientKind.REMOTE)) {
          val clientLookupManager = ClientLookupManager.getInstance(session) ?: continue
          if (event.editor == clientLookupManager.getActiveLookup()?.editor) {
            clientLookupManager.hideActiveLookup()
          }
        }
      }
    }, myProject)
  }

  override fun showLookup(editor: Editor,
                          items: Array<LookupElement>,
                          prefix: String,
                          arranger: LookupArranger): LookupEx? {
    val lookup = createLookup(editor, items, prefix, arranger)
    return if (lookup.showLookup()) lookup else null
  }

  @RequiresEdt
  override fun createLookup(editor: Editor,
                            items: Array<LookupElement>,
                            prefix: String,
                            arranger: LookupArranger): LookupImpl {
    return ClientLookupManager.getCurrentInstance(myProject).createLookup(editor, items, prefix, arranger)
  }

  fun fireActiveLookupChanged(oldLookup: LookupImpl?, newLookup: LookupImpl?) {
    LOG.runAndLogException {
      myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, oldLookup, newLookup)
    }
    LOG.runAndLogException {
      myProject.messageBus.syncPublisher(LookupManagerListener.TOPIC).activeLookupChanged(oldLookup, newLookup)
    }
  }

  override fun hideActiveLookup() {
    ClientLookupManager.getCurrentInstance(myProject).hideActiveLookup()
  }

  override fun getActiveLookup(): LookupEx? {
    return ClientLookupManager.getCurrentInstance(myProject).getActiveLookup()
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener)
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener, disposable: Disposable) {
    addPropertyChangeListener(listener)
    Disposer.register(disposable) { removePropertyChangeListener(listener) }
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener)
  }


  @TestOnly
  fun forceSelection(completion: Char, index: Int) {
    val activeLookup = ClientLookupManager.getCurrentInstance(myProject).getActiveLookup()
                       ?: throw RuntimeException("There are no items in this lookup")
    val lookupItem = activeLookup.items[index]
    activeLookup.currentItem = lookupItem
    (activeLookup as LookupImpl).finishLookup(completion)
  }

  @TestOnly
  fun forceSelection(completion: Char, item: LookupElement?) {
    val activeLookup = ClientLookupManager.getCurrentInstance(myProject).getActiveLookup()
                       ?: throw RuntimeException("There are no items in this lookup")
    activeLookup.currentItem = item
    (activeLookup as LookupImpl).finishLookup(completion)
  }

  @TestOnly
  fun clearLookup() {
    ClientLookupManager.getCurrentInstance(myProject).clear()
  }

  companion object {
    private val LOG = Logger.getInstance(LookupManagerImpl::class.java)
    val SUPPRESS_AUTOPOPUP_JAVADOC: Key<Boolean> = Key.create("LookupManagerImpl.suppressAutopopupJavadoc")

    @JvmStatic
    fun isAutoPopupJavadocSupportedBy(lookupElement: LookupElement): Boolean {
      return lookupElement.getUserData(SUPPRESS_AUTOPOPUP_JAVADOC) == null
    }

    fun getActiveLookup(editor: Editor?): LookupEx? = LookupManager.getActiveLookup(editor)

    fun getInstance(project: Project): LookupManager = LookupManager.getInstance(project)
  }
}
