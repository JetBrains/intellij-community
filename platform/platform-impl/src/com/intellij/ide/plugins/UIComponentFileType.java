// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class UIComponentFileType extends FakeFileType {
    public static final UIComponentFileType INSTANCE = new UIComponentFileType();

    private UIComponentFileType() {
    }

    @Override
    public @NonNls @NotNull String getName() {
      return "UI File";
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
      return "UI File Type description"; //NON-NLS
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
      return "UI File type"; //NON-NLS
    }

    @Override
    public boolean isMyFileType(@NotNull VirtualFile file) {
      return file instanceof UIComponentVirtualFile;
    }
}
