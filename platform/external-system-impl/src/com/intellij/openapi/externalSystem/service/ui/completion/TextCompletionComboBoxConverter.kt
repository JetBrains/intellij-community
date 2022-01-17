// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

interface TextCompletionComboBoxConverter<T> {

  fun createItem(text: String): T

  fun createString(element: T): String
}

