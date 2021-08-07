// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.presentation;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @see Presentation
 */
public abstract class PresentationProvider<T> {

  @Nullable
  public String getName(T t) { return null; }

  @Nullable
  public Icon getIcon(T t) { return null; }

  @Nullable
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getTypeName(T t) { return null; }
}
