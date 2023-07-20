// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.presentation;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @see Presentation
 */
public abstract class PresentationProvider<T> {

  public @Nullable String getName(T t) { return null; }

  public @Nullable Icon getIcon(T t) { return null; }

  public @Nullable @Nls(capitalization = Nls.Capitalization.Title) String getTypeName(T t) { return null; }
}
