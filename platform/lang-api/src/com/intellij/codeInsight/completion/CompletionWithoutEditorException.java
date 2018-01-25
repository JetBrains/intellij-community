// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

/**
 * An exception thrown when a completion contributor attempts to invoke {@link CompletionParameters#getEditor()}
 * for a completion performed in absence of editor. This exception is caught by completion infrastructure, and so
 * shouldn't be handled by contributors. To avoid this exception, check {@link CompletionParameters#hasEditor()}.
 * 
 * @since 181.*
 */
public class CompletionWithoutEditorException extends RuntimeException {
}
