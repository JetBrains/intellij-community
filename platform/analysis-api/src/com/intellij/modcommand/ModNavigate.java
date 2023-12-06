// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A command that updates the selection and/or caret position in the editor
 * 
 * @param file file that should be opened in the editor. The command aborts if the selected editor contains another file
 * @param selectionStart selection start; -1 if selection should not be changed
 * @param selectionEnd selection end; -1 if selection should not be changed
 * @param caret caret position; -1 if caret position should not be changed
 */
public record ModNavigate(@NotNull VirtualFile file, int selectionStart, int selectionEnd, int caret) implements ModCommand {
}
