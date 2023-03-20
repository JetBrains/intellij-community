// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.ExtensionPointName

interface ImplementationViewDocumentFactory{
    fun createDocument(element: ImplementationViewElement) : Document? fun tuneEditorBeforeShow(editor: EditorEx) = Unit fun tuneEditorAfterShow(editor: EditorEx) = Unit

    companion object {
        @JvmField val EP_NAME = ExtensionPointName.create<ImplementationViewDocumentFactory>("com.intellij.implementationViewDocumentFactory")
    }
}
