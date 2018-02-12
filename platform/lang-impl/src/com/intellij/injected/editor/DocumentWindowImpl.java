/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.injected.editor;

import com.intellij.openapi.util.UserDataHolderBase;

/**
 * @deprecated use {@link DocumentWindow} instead
 */
@Deprecated
public abstract class DocumentWindowImpl extends UserDataHolderBase implements DocumentWindow {
  public abstract int hostToInjectedUnescaped(int hostOffset);
}
