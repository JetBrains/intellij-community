// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import java.util.Comparator;

public final class IndexComparator implements Comparator<NodeDescriptor<?>> {
  public static final IndexComparator INSTANCE = new IndexComparator();

  private IndexComparator() {}

  @Override
  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    return nodeDescriptor1.getIndex() - nodeDescriptor2.getIndex();
  }
}
