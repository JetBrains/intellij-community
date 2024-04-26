// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class ModifierListElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(ModifierListElement.class);

  public ModifierListElement() {
    super(JavaElementType.MODIFIER_LIST);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (before == null) {
      if (first == last && ElementType.KEYWORD_BIT_SET.contains(first.getElementType())) {
        anchor = getDefaultAnchor((PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(this),
                                  (PsiKeyword)SourceTreeToPsiMap.treeElementToPsi(first));
        before = Boolean.TRUE;
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == JavaElementType.ANNOTATION) return ChildRole.ANNOTATION;
    return ChildRoleBase.NONE;
  }

  private static final HashMap<String, Integer> ourModifierToOrderMap = new HashMap<>();

  static { //TODO : options?
    ourModifierToOrderMap.put(PsiModifier.PUBLIC, 1);
    ourModifierToOrderMap.put(PsiModifier.PRIVATE, 1);
    ourModifierToOrderMap.put(PsiModifier.PROTECTED, 1);
    ourModifierToOrderMap.put(PsiModifier.STATIC, 2);
    ourModifierToOrderMap.put(PsiModifier.ABSTRACT, 2);
    ourModifierToOrderMap.put(PsiModifier.FINAL, 3);
    ourModifierToOrderMap.put(PsiModifier.SYNCHRONIZED, 4);
    ourModifierToOrderMap.put(PsiModifier.TRANSIENT, 4);
    ourModifierToOrderMap.put(PsiModifier.VOLATILE, 4);
    ourModifierToOrderMap.put(PsiModifier.NATIVE, 5);
    ourModifierToOrderMap.put(PsiModifier.STRICTFP, 6);
  }

  private static @Nullable ASTNode getDefaultAnchor(PsiModifierList modifierList, PsiKeyword modifier) {
    Integer order = ourModifierToOrderMap.get(modifier.getText());
    if (order == null) return null;
    boolean hasKeyword = false;
    for (ASTNode child = SourceTreeToPsiMap.psiToTreeNotNull(modifierList).getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (ElementType.KEYWORD_BIT_SET.contains(child.getElementType())) {
        hasKeyword = true;
        Integer order1 = ourModifierToOrderMap.get(child.getText());
        if (order1 == null) continue;
        if (order1.intValue() > order.intValue()) {
          return child;
        }
      } else if (child.getElementType() == JavaElementType.ANNOTATION && hasKeyword) {
        return child;
      }
    }
    return null;
  }
}
