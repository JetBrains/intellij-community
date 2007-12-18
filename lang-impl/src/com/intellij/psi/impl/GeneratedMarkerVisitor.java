package com.intellij.psi.impl;

import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;

public class GeneratedMarkerVisitor extends RecursiveTreeElementVisitor {
  protected boolean visitNode(TreeElement element) {
    CodeEditUtil.setNodeGenerated(element, true);
    return true;
  }
}
