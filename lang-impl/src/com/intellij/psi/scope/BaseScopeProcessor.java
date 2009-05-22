package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;

public abstract class BaseScopeProcessor implements PsiScopeProcessor{

  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated){}
}
