// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.treeView.TreeAnchorizer;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

final class AnchoredSet {
  private final Set<Object> myAnchors;

  AnchoredSet(@NotNull Set<Object> elements) {
    myAnchors = new LinkedHashSet<>(TreeAnchorizer.anchorizeList(elements));
  }

  @NotNull
  Set<Object> getElements() {
    return new LinkedHashSet<>(TreeAnchorizer.retrieveList(myAnchors));
  }
}
