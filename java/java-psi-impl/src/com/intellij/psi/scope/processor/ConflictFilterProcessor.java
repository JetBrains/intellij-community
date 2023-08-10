// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConflictFilterProcessor extends FilterScopeProcessor<CandidateInfo> implements NameHint {
  private final PsiConflictResolver[] myResolvers;
  private JavaResolveResult[] myCachedResult;
  protected String myName;
  protected final PsiElement myPlace;
  protected final PsiFile myPlaceFile;

  public ConflictFilterProcessor(String name,
                                 @NotNull ElementFilter filter,
                                 PsiConflictResolver @NotNull [] resolvers,
                                 @NotNull List<CandidateInfo> container,
                                 @NotNull PsiElement place,
                                 @NotNull PsiFile placeFile) {
    super(filter, container);
    myResolvers = resolvers;
    myName = name;
    myPlace = place;
    myPlaceFile = placeFile;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    JavaResolveResult[] cachedResult = myCachedResult;
    if (cachedResult != null && cachedResult.length == 1 && stopAtFoundResult(cachedResult[0])) {
      return false;
    }
    if (myName == null || PsiUtil.checkName(element, myName, myPlace)) {
      return super.execute(element, state);
    }
    return true;
  }

  protected boolean stopAtFoundResult(@NotNull JavaResolveResult cachedResult) {
    return cachedResult.isAccessible();
  }

  @Override
  protected void add(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
    add(new CandidateInfo(element, substitutor));
  }

  protected void add(@NotNull CandidateInfo info) {
    myCachedResult = null;
    myResults.add(info);
  }

  @Override
  public void handleEvent(@NotNull PsiScopeProcessor.Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.CHANGE_LEVEL && myName != null) {
      getResult();
    }
  }

  public JavaResolveResult @NotNull [] getResult() {
    JavaResolveResult[] cachedResult = myCachedResult;
    if (cachedResult == null) {
      List<CandidateInfo> conflicts = getResults();
      if (!conflicts.isEmpty()) {
        for (PsiConflictResolver resolver : myResolvers) {
          CandidateInfo candidate = resolver.resolveConflict(conflicts);
          if (candidate != null) {
            conflicts.clear();
            conflicts.add(candidate);
            break;
          }
        }
      }
      myCachedResult = cachedResult = conflicts.toArray(JavaResolveResult.EMPTY_ARRAY);
    }

    return cachedResult;
  }

  @Override
  public String getName(@NotNull ResolveState state) {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == NameHint.KEY) {
      return myName != null ? (T)this : null;
    }
    return super.getHint(hintKey);
  }
}
