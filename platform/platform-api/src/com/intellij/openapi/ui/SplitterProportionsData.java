/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

public interface SplitterProportionsData extends JDOMExternalizable {
  void saveSplitterProportions(Component root);

  void restoreSplitterProportions(Component root);

  void externalizeToDimensionService(@NonNls String key);

  void externalizeFromDimensionService(@NonNls String key);
}