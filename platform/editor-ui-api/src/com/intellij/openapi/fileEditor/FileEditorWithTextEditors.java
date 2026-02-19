// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The marker interface for composite {@link TextEditor}, where e.g., highlightings can be delegated to embedded editors.
 *
 * @see com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
 */
public interface FileEditorWithTextEditors extends FileEditor {
  @NotNull List<? extends @NotNull Editor> getEmbeddedEditors();
}
