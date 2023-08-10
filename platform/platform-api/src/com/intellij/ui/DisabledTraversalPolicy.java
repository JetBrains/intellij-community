// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.awt.*;

/**
 * Disables traversal policy for component and all children.
 * Useful when component should be removed from tab order.
 * Works in pair with {@link Container#setFocusTraversalPolicyProvider(boolean)}
 */
public class DisabledTraversalPolicy extends FocusTraversalPolicy {

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    return null;
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    return null;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    return null;
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    return null;
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    return null;
  }
}
