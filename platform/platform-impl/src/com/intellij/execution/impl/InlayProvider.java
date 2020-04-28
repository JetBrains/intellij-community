// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface InlayProvider {
  EditorCustomElementRenderer createInlayRenderer(Editor editor);
}
