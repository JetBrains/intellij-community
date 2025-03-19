// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.remote.RemoteSearchEverywhereConverter
import com.intellij.ide.actions.searcheverywhere.remote.RemoteSearchEverywhereConverterSupplier
import com.intellij.ide.actions.searcheverywhere.remote.SearchEverywhereRemoteSupportService
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereRemoteSupportServiceImpl: SearchEverywhereRemoteSupportService {

  override fun getConverters(contributorID: String?): List<RemoteSearchEverywhereConverter<*, *>> {
    val res = mutableListOf<RemoteSearchEverywhereConverter<*, *>>()
    RemoteSearchEverywhereConverterSupplier.EP_NAME.extensionList
      .filter { it.contributorsList().contains(contributorID) }
      .forEach { res.add(it.createConverter()) }

    return res
  }

}