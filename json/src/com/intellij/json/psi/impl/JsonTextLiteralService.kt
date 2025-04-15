// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.time.Duration

@Service
internal class JsonTextLiteralService : Disposable {
  private val unquoteAndUnescapeCache: LoadingCache<String, String> = Caffeine.newBuilder()
    .maximumSize(2048)
    .expireAfterAccess(Duration.ofMinutes(5))
    .executor(Dispatchers.Default.asExecutor())
    .build { key ->
      val text = JsonPsiUtil.stripQuotes(key)
      if (text.indexOf('\\') >= 0) StringUtil.unescapeStringCharacters(text) else text
    }

  init {
    LowMemoryWatcher.register(Runnable { unquoteAndUnescapeCache.invalidateAll() }, this)
  }

  fun unquoteAndUnescape(str: String): String =
    unquoteAndUnescapeCache.get(str)

  override fun dispose() {
  }

  companion object {
    @JvmStatic
    val instance: JsonTextLiteralService
      get() = ApplicationManager.getApplication().service()
  }

}