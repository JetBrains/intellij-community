package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.json.psi.JsonElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonElementImpl extends ASTWrapperPsiElement implements JsonElement {

  public JsonElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    final String className = getClass().getSimpleName();
    return StringUtil.trimEnd(className, "Impl");
  }
}
