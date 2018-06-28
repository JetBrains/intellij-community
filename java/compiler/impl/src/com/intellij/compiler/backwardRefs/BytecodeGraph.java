// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.psi.PsiElement;
import org.jetbrains.jps.backwardRefs.pwa.ClassFileSymbol;

import java.util.*;

public class BytecodeGraph {

  private final Map<ClassFileSymbol, JvmNode> myNodes = new HashMap<>();
  private final Set<JvmNode> myUnusedNodes = new HashSet<>();
  private final int myMainMethodName;

  public BytecodeGraph(int mainMethodName) {
    myMainMethodName = mainMethodName;
  }

  public void addNode(JvmNode node) {
    if (node.getSymbol() instanceof ClassFileSymbol.Method && node.getSymbol().name == myMainMethodName) {
      node.setImplicitlyUsed();
    }
    myNodes.put(node.getSymbol(), node);
  }

  public JvmNode getNodeFromSources(ClassFileSymbol symbol) {
    return myNodes.get(symbol);
  }

  public void graphBuilt() {
    myUnusedNodes.clear();
    myNodes.values().forEach(n -> isUsed(n));
    System.out.println("---");
    myNodes.values().forEach(n -> {
      System.out.println(isUsed(n));
    });

  }

  public boolean isSomeCodeUnused() {
    return !myUnusedNodes.isEmpty();
  }

  public Set<JvmNode> getUnusedNodes() {
    return myUnusedNodes;
  }

  private boolean isUsed(JvmNode n) {
    boolean used = n.isUsed();
    if (!used) {
      myUnusedNodes.add(n);
    }
    return used;
  }


  public JvmNode findFor(PsiElement element) {
    return null;
  }

  public PsiElement resolveNode(JvmNode node) {
    return null;
  }
}
