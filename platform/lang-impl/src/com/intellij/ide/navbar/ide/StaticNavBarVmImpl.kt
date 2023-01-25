// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.navbar.vm.StaticNavBarVm
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.flow.throttle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class StaticNavBarVmImpl(
  coroutineScope: CoroutineScope,
  project: Project,
  initiallyVisible: Boolean,
) : StaticNavBarVm {

  private val _isVisible: MutableStateFlow<Boolean> = MutableStateFlow(initiallyVisible)

  var isVisible: Boolean
    get() {
      return _isVisible.value
    }
    set(value) {
      _isVisible.value = value
    }

  private val _vm: MutableStateFlow<NavBarVmImpl?> = MutableStateFlow(null)
  override val vm: StateFlow<NavBarVm?> = _vm.asStateFlow()

  init {
    coroutineScope.launch {
      _isVisible.collectLatest { visible ->
        if (!visible) {
          _vm.value = null
        }
        else {
          supervisorScope {
            _vm.value = NavBarVmImpl(
              this@supervisorScope, // scope will die once [_isVisible] changes
              project,
              initialItems = defaultModel(project),
              activityFlow = activityFlow(project).flowOn(Dispatchers.EDT).throttle(DEFAULT_UI_RESPONSE_TIMEOUT),
            )
          }
        }
      }
    }
  }
}
