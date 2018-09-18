// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scopeView.nodes;

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

public class ClassNode extends MemberNode<PsiClass> implements Comparable<ClassNode>{
  public ClassNode(final PsiClass aClass) {
    super(aClass);
  }

  public String toString() {
    final PsiClass aClass = (PsiClass)getPsiElement();
    return aClass != null && aClass.isValid() ? ClassPresentationUtil.getNameForClass(aClass, false) : "";
  }

  @Override
  public int compareTo(final ClassNode o) {
    final int comparision = ClassTreeNode.getClassPosition((PsiClass)getPsiElement()) - ClassTreeNode.getClassPosition((PsiClass)o.getPsiElement());
    if (comparision == 0) {
      return toString().compareToIgnoreCase(o.toString());
    }
    return comparision;
  }
}
