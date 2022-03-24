// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @deprecated Please use com.intellij.codeInsight.editorActions.TypedHandlerDelegate instead
 */
@Deprecated(forRemoval = true)
public final class EditorTypedHandlerBean {
  // these must be public for scrambling compatibility
  @Attribute("implementationClass")
  public String implementationClass;

  TypedActionHandler handler;
}