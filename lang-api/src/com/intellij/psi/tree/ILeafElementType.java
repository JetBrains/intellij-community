/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface ILeafElementType {

  @NotNull
  ASTNode createLeafNode(CharSequence text, int start, int end, CharTable table);

}