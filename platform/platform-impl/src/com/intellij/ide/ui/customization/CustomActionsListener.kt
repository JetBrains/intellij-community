// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

/**
 * Listens to changes to global action schema [CustomActionsSchema.getInstance].
 *
 * Use [CustomActionsListener.subscribe] to start listening to changes or
 * [CustomActionsListener.fireSchemaChanged] to notify all listeners about changes.
 */
interface CustomActionsListener {

  /**
   * Is called when global action schema is changed.
   *
   * So toolbars can be dynamically updated according to these changes.
   */
  fun schemaChanged()

  companion object {
    private val TOPIC = Topic.create("CustomizableActionGroup changed", CustomActionsListener::class.java)

    /**
     * Subscribe for changes in global action schema.
     */
    @JvmStatic
    fun subscribe(disposable: Disposable, listener: CustomActionsListener) {
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(TOPIC, listener)
    }

    /**
     * Notify all listeners about global schema changes.
     */
    @JvmStatic
    fun fireSchemaChanged() {
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).schemaChanged()
    }
  }

}