// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * Use theme instead of IntelliJLaf customization
 * @author Konstantin Bulenkov
 */
@Deprecated(forRemoval = true)
public class IntelliJLaf extends DarculaLaf {
  public static final @NlsSafe String NAME = "IntelliJ";

  public IntelliJLaf() {
    super(false);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected @NotNull String getPrefix() {
    return "themes/intellijlaf";
  }
}
