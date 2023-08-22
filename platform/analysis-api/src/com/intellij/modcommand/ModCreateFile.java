// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import org.jetbrains.annotations.NotNull;

/**
 * @param file file to create
 * @param text file content
 */
public record ModCreateFile(@NotNull FutureVirtualFile file, @NotNull String text) implements ModCommand {
}
