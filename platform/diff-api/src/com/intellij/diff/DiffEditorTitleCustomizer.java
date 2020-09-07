// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface DiffEditorTitleCustomizer {
  /**
   * @return component that will be used to replace an editor title
   */
  @NotNull
  JComponent getLabel();
}
