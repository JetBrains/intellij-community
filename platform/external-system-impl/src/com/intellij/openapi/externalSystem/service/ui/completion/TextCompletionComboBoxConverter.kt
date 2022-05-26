// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

interface TextCompletionComboBoxConverter<T> : TextCompletionRenderer<T> {

  fun getItem(text: String): T
}