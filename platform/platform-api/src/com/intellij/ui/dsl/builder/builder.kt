// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.ui.DialogPanel

/**
 * The root panel that provided by [init] does not support [CellBase] methods now. May be added later but seems not needed now
 */
fun panel(init: Panel.() -> Unit): DialogPanel {
  return KotlinUiDslService.getInstance().panel(init)
}
