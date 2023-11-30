// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.enums

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

enum class SortBy(val presentableNameSupplier: Supplier<@Nls String>, val query: String, val mpParameter: String) {
  UPDATE_DATE(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Updated"), "updated", "orderBy=update+date"),
  DOWNLOADS(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Downloads"), "downloads", "orderBy=downloads"),
  RATING(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Rating"), "rating", "orderBy=rating"),
  NAME(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Name"), "name", "orderBy=name"),
  RELEVANCE(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Relevance"), "relevance", "orderBy=relevance");


  companion object {
    @JvmStatic
    fun getByQueryOrNull(query: String): SortBy? = entries.find { it.query == query }
  }
}