/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;

public interface ReadonlyFragmentModificationHandler {
  void handle(ReadOnlyFragmentModificationException e);
}