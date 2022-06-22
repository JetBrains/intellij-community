// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.util.text.nullize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.swing.JTextField

class ReviewListSearchTextFieldFactory(private val searchState: MutableStateFlow<String?>) {

  fun create(vmScope: CoroutineScope): JTextField {
    val searchField = JTextField()
    searchField.addActionListener {
      val text = searchField.text.nullize()
      searchState.update { text }
    }
    vmScope.launch {
      searchState.collectLatest {
        if (searchField.text.nullize() != it) searchField.text = it
      }
    }
    return searchField
  }
}