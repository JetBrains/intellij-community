// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import java.util.concurrent.atomic.AtomicInteger

class SearchEverywhereSessionService {
  private val counter = AtomicInteger()

  /**
   * Current id is always the last opened session id
   */
  fun getCurrentSessionId() = counter.get()

  fun incAndGet() = counter.incrementAndGet()
}