// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*/
public abstract class PatternDescriptor {

  public static final String ROOT = "root";

  public @Nullable @NonNls String getId() {
    return null;
  }

  public abstract @NotNull String getParentId();

  public abstract @NotNull @NlsActions.ActionText String getName();

  public abstract @Nullable Icon getIcon();

  public abstract @Nullable Template getTemplate();

  public abstract void actionPerformed(DataContext context);
}
