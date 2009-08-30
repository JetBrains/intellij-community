package com.intellij.debugger.ui.tree;

import com.intellij.openapi.util.Key;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface NodeDescriptor {

  String getName();
  String getLabel();

  <T> T getUserData(Key<T> key);
  <T> void putUserData(Key<T> key, T value);

  void displayAs(NodeDescriptor descriptor);

  void setAncestor(NodeDescriptor oldDescriptor);
}
