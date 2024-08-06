/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonLiteral;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

abstract class JsonLiteralMixin extends JsonElementImpl implements JsonLiteral {
  protected JsonLiteralMixin(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
