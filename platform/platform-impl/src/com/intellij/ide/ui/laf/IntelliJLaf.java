// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class IntelliJLaf extends DarculaLaf {
  public static final String NAME = "IntelliJ";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  protected String getPrefix() {
    return "intellijlaf";
  }

  @Override
  protected DefaultMetalTheme createMetalTheme() {
    return new IdeaBlueMetalTheme();
  }
}
