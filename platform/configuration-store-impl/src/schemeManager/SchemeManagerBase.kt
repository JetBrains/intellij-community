// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeProcessor

abstract class SchemeManagerBase<T: Scheme, in MUTABLE_SCHEME : T>(internal val processor: SchemeProcessor<T, MUTABLE_SCHEME>) : SchemeManager<T>() {
  /**
   * Schemes can be lazily loaded, so, a client should be able to set a current scheme by name, not only by instance.
   */
  @Volatile
  internal var currentPendingSchemeName: String? = null

  @Volatile
  override var activeScheme: T? = null
    internal set

  override var currentSchemeName: String?
    get() = activeScheme?.let { processor.getSchemeKey(it) } ?: currentPendingSchemeName
    set(schemeName) = setCurrentSchemeName(schemeName, true)

  internal fun processPendingCurrentSchemeName(newScheme: T): Boolean {
    LOG.debug { "processPendingCurrentSchemeName: newScheme=$newScheme, currentPendingSchemeName=$currentPendingSchemeName"}
    if (processor.getSchemeKey(newScheme) == currentPendingSchemeName) {
      setCurrent(newScheme, false)
      return true
    }
    return false
  }

  override fun setCurrent(scheme: T?, notify: Boolean, processChangeSynchronously: Boolean) {
    LOG.debug { "setCurrent: scheme=$scheme" }
    currentPendingSchemeName = null

    val oldCurrent = activeScheme
    activeScheme = scheme
    if (notify && oldCurrent !== scheme) {
      processor.onCurrentSchemeSwitched(oldScheme = oldCurrent, newScheme = scheme, processChangeSynchronously = processChangeSynchronously)
    }
  }

  override fun setCurrentSchemeName(schemeName: String?, notify: Boolean) {
    currentPendingSchemeName = schemeName
    LOG.debug { "setCurrentSchemeName: schemeName=$schemeName" }

    val scheme = schemeName?.let { findSchemeByName(it) }
    LOG.debug { "setCurrentSchemeName: scheme=$scheme" }
    // don't set a current scheme if no scheme by name - pending resolution (see currentSchemeName field comment)
    if (scheme != null || schemeName == null) {
      setCurrent(scheme, notify)
    }
  }
}