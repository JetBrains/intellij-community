// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface LightEditTabAttributesProvider {
  ExtensionPointName<LightEditTabAttributesProvider> EP_NAME
    = ExtensionPointName.create("com.intellij.lightEditTabAttributesProvider");

  @Nullable
  TextAttributes calcAttributes(@NotNull LightEditorInfo editorInfo);
}
