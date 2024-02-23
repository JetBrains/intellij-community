// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.TestOnly
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

open class LookupManagerImpl(private val myProject: Project) : LookupManager() {
  private var myActiveLookup: LookupImpl? = null
  private var myActiveLookupEditor: Editor? = null

  @Deprecated("Deprecated as the methods using the field")
  private val myPropertyChangeSupport = PropertyChangeSupport(this)

  init {
    val connection = myProject.messageBus.connect()
    connection.subscribe(EditorHintListener.TOPIC, object : EditorHintListener {
      override fun hintShown(editor: Editor, hint: LightweightHint, flags: Int, hintInfo: HintHint) {
        if (editor.project === myProject) {
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
        if (event.editor === myActiveLookupEditor) {
          hideActiveLookup()
        }
      }
    }, myProject)
  }

  override fun showLookup(editor: Editor,
                          items: Array<LookupElement>,
                          prefix: String,
                          arranger: LookupArranger): LookupEx? {
    for (item in items) {
      checkNotNull(item)
    }

    val lookup = createLookup(editor, items, prefix, arranger)
    return if (lookup.showLookup()) lookup else null
  }

  override fun createLookup(editor: Editor,
                            items: Array<LookupElement>,
                            prefix: String,
                            arranger: LookupArranger): LookupImpl {
    hideActiveLookup()

    val lookup = createLookup(editor, arranger, myProject)

    ThreadingAssertions.assertEventDispatchThread()

    myActiveLookup = lookup
    myActiveLookupEditor = editor
    Disposer.register(lookup) {
      myActiveLookup = null
      myActiveLookupEditor = null
      fireActiveLookupChanged(lookup, null)
    }

    if (items.size > 0) {
      val matcher = CamelHumpMatcher(prefix)
      for (item in items) {
        myActiveLookup!!.addItem(item, matcher)
      }
      myActiveLookup!!.refreshUi(true, true)
    }

    fireActiveLookupChanged(null, myActiveLookup)
    return lookup
  }

  fun fireActiveLookupChanged(oldLookup: LookupImpl?, newLookup: LookupImpl?) {
    myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, oldLookup, newLookup)
    myProject.messageBus.syncPublisher(LookupManagerListener.TOPIC).activeLookupChanged(oldLookup, newLookup)
  }

  protected open fun createLookup(editor: Editor, arranger: LookupArranger, project: Project): LookupImpl {
    return LookupImpl(project, editor, arranger)
  }

  override fun hideActiveLookup() {
    val lookup = myActiveLookup
    if (lookup != null) {
      lookup.checkValid()
      lookup.hide()
      LOG.assertTrue(lookup.isLookupDisposed, "Should be disposed")
    }
  }

  override fun getActiveLookup(): LookupEx? {
    val activeLookup = myActiveLookup
    if (activeLookup != null && activeLookup.isLookupDisposed) {
      val lookup: LookupImpl = activeLookup
      myActiveLookup = null
      lookup.checkValid()
    }

    return activeLookup
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
    if (myActiveLookup == null) throw RuntimeException("There are no items in this lookup")
    val lookupItem = myActiveLookup!!.items[index]
    myActiveLookup!!.currentItem = lookupItem
    myActiveLookup!!.finishLookup(completion)
  }

  @TestOnly
  fun forceSelection(completion: Char, item: LookupElement?) {
    myActiveLookup!!.currentItem = item
    myActiveLookup!!.finishLookup(completion)
  }

  @TestOnly
  fun clearLookup() {
    if (myActiveLookup != null) {
      myActiveLookup!!.hide()
      myActiveLookup = null
    }
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
