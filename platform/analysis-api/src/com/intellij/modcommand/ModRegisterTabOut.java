// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A command that registers the tab-out range in the editor. May do nothing if the editor doesn't support the tab out
 *
 * @param file           file that should be opened in the editor.
 * @param rangeStart     start of the range from where the tab-out operation could be performed
 * @param rangeEnd       end of the range from where the tab-out operation could be performed
 * @param target         target position where the caret should be placed after tab-out operation
 */
public record ModRegisterTabOut(@NotNull VirtualFile file, int rangeStart, int rangeEnd, int target) implements ModCommand {
}
