/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.ui.SimpleColoredComponent;

import javax.swing.*;

/**
 * @author peter
 */
public interface MemberChooserObject {
  void renderTreeNode(SimpleColoredComponent component, JTree tree);

  String getText();

  boolean equals(Object o);

  int hashCode();
}
