// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search

import com.intellij.openapi.options.Configurable
import java.util.*

data class ConfigurableHit(val nameHits: Set<Configurable>, val nameFullHits: Set<Configurable>, val contentHits: Set<Configurable>) {
  val all: Set<Configurable>
    get() {
      val all = LinkedHashSet<Configurable>(nameHits.size + contentHits.size)
      all.addAll(nameHits)
      all.addAll(contentHits)
      return all
    }
}
