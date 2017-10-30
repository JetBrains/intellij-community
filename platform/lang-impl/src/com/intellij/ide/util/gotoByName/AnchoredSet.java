// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.util.containers.ContainerUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
class AnchoredSet {
  private final Set<Object> myAnchors;

  AnchoredSet(Set<Object> elements) {
    this.myAnchors = new LinkedHashSet<>(ContainerUtil.map(elements, TreeAnchorizer.getService()::createAnchor));
  }

  Set<Object> getElements() {
    return new LinkedHashSet<>(ContainerUtil.mapNotNull(myAnchors, TreeAnchorizer.getService()::retrieveElement));
  }

}
