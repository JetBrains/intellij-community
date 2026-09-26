// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

sealed class LspGoToDefinitionCustomizer

open class LspGoToDefinitionSupport : LspGoToDefinitionCustomizer()

object LspGoToDefinitionDisabled : LspGoToDefinitionCustomizer()
