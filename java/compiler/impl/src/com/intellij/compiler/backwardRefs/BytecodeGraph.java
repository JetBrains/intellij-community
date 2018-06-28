// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.jps.backwardRefs.pwa.ClassFileSymbol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BytecodeGraph {

  private final Map<ClassFileSymbol, JvmNode> myNodes = new HashMap<>();
  private final ClearableLazyValue<List<JvmNode>> myUnusedNodes =
    ClearableLazyValue.create(() -> myNodes.values().stream().filter(JvmNode::isUsed).collect(Collectors.toList()));

  public void addNode(JvmNode node) {
    myNodes.put(node.getSymbol(), node);
  }

  public JvmNode getNodeFromSources(ClassFileSymbol symbol) {
    return myNodes.get(symbol);
  }

  public void graphBuilt() {
    myNodes.values().forEach(n -> n.isUsed());
  }

  public JvmNode findFor(PsiElement element) {
    return null;
  }

  public PsiElement resolveNode(JvmNode node) {
    return null;
  }
}
