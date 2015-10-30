/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
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
    List<IElementType> types = ContainerUtil.newSmartList(((IElementType)JavaElementType.CODE_BLOCK));
    return AlignmentStrategy.createAlignmentPerTypeStrategy(types, JavaElementType.METHOD, true);
  }
  
}
