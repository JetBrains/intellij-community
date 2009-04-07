package com.intellij.ide.util.treeView;

public interface TreeVisitor<T> {

  boolean visit(T node);
  
}