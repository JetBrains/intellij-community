// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import org.jetbrains.annotations.ApiStatus.Internal;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;

@Internal
public class AbstractDelegatingToRootTraversalPolicy extends FocusTraversalPolicy {

  @Override
  public Component getComponentAfter(final Container aContainer, final Component aComponent) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getComponentAfter(cycleRootAncestor, aContainer);
  }

  @Override
  public Component getComponentBefore(final Container aContainer, final Component aComponent) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getComponentBefore(cycleRootAncestor, aContainer);
  }

  @Override
  public Component getFirstComponent(final Container aContainer) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getFirstComponent(cycleRootAncestor);
  }

  @Override
  public Component getLastComponent(final Container aContainer) {
    final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
    return cycleRootAncestor.getFocusTraversalPolicy().getLastComponent(cycleRootAncestor);
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    return aContainer;
  }
}
