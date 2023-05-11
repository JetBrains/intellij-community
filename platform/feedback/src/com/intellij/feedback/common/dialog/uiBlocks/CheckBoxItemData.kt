// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts

data class CheckBoxItemData(val property: ObservableMutableProperty<Boolean>,
                            @NlsContexts.Checkbox val label: String)
