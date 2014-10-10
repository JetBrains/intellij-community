package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonLiteral;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

abstract class JsonLiteralMixin extends JsonElementImpl implements JsonLiteral {
  private final Object myRefLock = new Object();
  private volatile PsiReference[] myRefs;
  private volatile long myModCount = -1;

  protected JsonLiteralMixin(ASTNode node) {
    super(node);
  }

  // TODO AppCode legacy code, may worth to get rid of it in future
  @NotNull
  @Override
  public PsiReference[] getReferences() {
    final long count = getManager().getModificationTracker().getModificationCount();
    if (count != myModCount) {
      synchronized (myRefLock) {
        if (count != myModCount) {
          myRefs = ReferenceProvidersRegistry.getReferencesFromProviders(this);
          myModCount = count;
        }
      }
    }

    return myRefs;
  }
}
