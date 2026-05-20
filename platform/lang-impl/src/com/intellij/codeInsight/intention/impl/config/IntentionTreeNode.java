// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ui.CheckedTreeNode;

/**
 * @author Bas Leijdekkers
 */
final class IntentionTreeNode extends CheckedTreeNode {

  IntentionTreeNode(Object userObject) {
    super(userObject);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CheckedTreeNode other) {
      Object userObject = getUserObject();
      if (userObject == null ? other.getUserObject() == null : other.getUserObject().equals(userObject)) return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    Object userObject = getUserObject();
    return userObject == null ? -1 : userObject.hashCode();
  }
}
