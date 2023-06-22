// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @param file file to delete
 */
public record ModDeleteFile(@NotNull VirtualFile file) implements ModCommand {
}
