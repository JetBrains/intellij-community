// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
public abstract class ShapeTransform extends com.intellij.ui.tabs.impl.ShapeTransform {
  protected ShapeTransform(Rectangle shapeRect, int xTransform, int yTransform, boolean swap) {
    super(shapeRect, xTransform, yTransform, swap);
  }
}
