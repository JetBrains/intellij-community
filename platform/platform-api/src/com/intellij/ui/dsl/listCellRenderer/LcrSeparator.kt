// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@LcrDslMarker
interface LcrSeparator {

  var text: @NlsContexts.Separator String?
}