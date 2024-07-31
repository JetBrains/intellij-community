// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ConfigurableHit(
  @JvmField val nameHits: Collection<Configurable>,
  @JvmField val nameFullHits: Collection<Configurable>,
  @JvmField val contentHits: List<Configurable>,
  @JvmField val spotlightFilter: String,
) {
  val all: Set<Configurable>
    get() {
      val all = LinkedHashSet<Configurable>(nameHits.size + contentHits.size)
      all.addAll(nameHits)
      all.addAll(contentHits)
      return all
    }
}
