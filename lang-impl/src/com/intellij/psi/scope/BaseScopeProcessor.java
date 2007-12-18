package com.intellij.psi.scope;

import com.intellij.util.ReflectionCache;

public abstract class BaseScopeProcessor implements PsiScopeProcessor{

  public <T> T getHint(Class<T> hintClass) {
    if (ReflectionCache.isAssignable(hintClass, getClass())){
      return (T)this;
    }
    else{
      return null;
    }
  }

  public void handleEvent(Event event, Object associated){}
}
