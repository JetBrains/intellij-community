// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SubsequentOneLineMethodsAligner extends ChildAlignmentStrategyProvider {
  private AlignmentStrategy myAlignmentStrategy;


  public SubsequentOneLineMethodsAligner() {
    myAlignmentStrategy = newAlignmentStrategy();
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
    IElementType childType = child.getElementType();

    if (childType != JavaElementType.METHOD || child.textContains('\n')) {
      myAlignmentStrategy = newAlignmentStrategy();
      return AlignmentStrategy.getNullStrategy();
    }

    return myAlignmentStrategy;
  }

  private static AlignmentStrategy newAlignmentStrategy() {
    List<IElementType> types = new SmartList<>(JavaElementType.CODE_BLOCK);
    return AlignmentStrategy.createAlignmentPerTypeStrategy(types, JavaElementType.METHOD, true);
  }

}
