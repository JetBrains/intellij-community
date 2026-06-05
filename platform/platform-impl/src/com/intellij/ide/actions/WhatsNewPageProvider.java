// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface WhatsNewPageProvider {
  ExtensionPointName<WhatsNewPageProvider> EP_NAME = ExtensionPointName.create("com.intellij.whatsNewPageProvider");

  boolean isAvailable();

  void openWhatsNewPage(@NotNull Project project,
                        @NotNull String url,
                        boolean includePlatformData,
                        @Nullable HTMLEditorProvider.JsQueryHandler queryHandler);
}
