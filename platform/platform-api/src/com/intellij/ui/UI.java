// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author max
 * @deprecated will be removed in 2018.2
 */
@Deprecated
public class UI {
   private UI() {
  }

  public static Color getColor(@NonNls String id) {
    return JBColor.PINK;
  }
}
