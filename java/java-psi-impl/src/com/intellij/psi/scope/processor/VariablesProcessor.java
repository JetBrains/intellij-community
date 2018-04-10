// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class VariablesProcessor implements PsiScopeProcessor, ElementClassHint {
  private boolean myStaticScopeFlag;
  private final boolean myStaticSensitiveFlag;
  private final List<PsiVariable> myResultList;

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive){
    this(staticSensitive, new SmartList<>());
  }

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive, List<PsiVariable> list){
    myStaticSensitiveFlag = staticSensitive;
    myResultList = list;
  }

  protected abstract boolean check(PsiVariable var, ResolveState state);

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.VARIABLE || kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST;
  }

  /** Always return true since we wanna get all vars in scope */
  @Override
  public boolean execute(@NotNull PsiElement pe, @NotNull ResolveState state){
    if(pe instanceof PsiVariable){
      final PsiVariable pvar = (PsiVariable)pe;
      if(!myStaticSensitiveFlag || !myStaticScopeFlag || pvar.hasModifierProperty(PsiModifier.STATIC)){
        if(check(pvar, state)){
          myResultList.add(pvar);
        }
      }
    }
    return true;
  }

  @Override
  public final void handleEvent(@NotNull Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC)
      myStaticScopeFlag = true;
  }

  public int size(){
    return myResultList.size();
  }

  public PsiVariable getResult(int i){
    return myResultList.get(i);
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }

    return null;
  }
}
