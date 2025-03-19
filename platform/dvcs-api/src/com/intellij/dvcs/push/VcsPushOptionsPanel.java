// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class VcsPushOptionsPanel extends JPanel {

  public abstract @Nullable VcsPushOptionValue getValue();


  public @NotNull OptionsPanelPosition getPosition() {
    return OptionsPanelPosition.DEFAULT;
  }

  public enum OptionsPanelPosition {DEFAULT, SOUTH}
}
