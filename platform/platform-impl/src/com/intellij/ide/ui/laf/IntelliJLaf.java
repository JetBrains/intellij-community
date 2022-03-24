// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class IntelliJLaf extends DarculaLaf {
  public static final @NlsSafe String NAME = "IntelliJ";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  protected String getPrefix() {
    return "com/intellij/ide/ui/laf/intellijlaf";
  }
}
