// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.ide.IdeBundle
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

private class JBCefAppCacheInvalidator : CachesInvalidator() {

  override fun getComment(): String = IdeBundle.message("jcef.local.cache.invalidate.action.description")

  override fun getDescription(): String = IdeBundle.message("jcef.local.cache.invalidate.checkbox.description")

  override fun optionalCheckboxDefaultValue(): Boolean = false

  override fun invalidateCaches() {
    ApplicationManager.getApplication().service<JBCefAppCache>().markInvalidated()
  }
}
