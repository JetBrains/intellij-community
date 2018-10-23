// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ComponentCache {
  private int myComponentCount = -1;
  private int myContainerWidth = -1;
  private int myContainerHeight = -1;

  public boolean isCached(@NotNull Container parent) {
    int componentCount = parent.getComponentCount();
    int containerWidth = parent.getWidth();
    int containerHeight = parent.getHeight();

    if (myComponentCount == componentCount && myContainerWidth == containerWidth && myContainerHeight == containerHeight) {
      return true;
    }

    myComponentCount = componentCount;
    myContainerWidth = containerWidth;
    myContainerHeight = containerHeight;

    return false;
  }
}