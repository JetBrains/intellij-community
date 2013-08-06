/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author ik
 * Date: 31.03.2003
 */
public class ConflictFilterProcessor extends FilterScopeProcessor<CandidateInfo> implements NameHint {
  private final PsiConflictResolver[] myResolvers;
  private JavaResolveResult[] myCachedResult = null;
  protected String myName;
  protected final PsiElement myPlace;
  protected final PsiFile myPlaceFile;

  public ConflictFilterProcessor(String name,
                                 @NotNull ElementFilter filter,
                                 @NotNull PsiConflictResolver[] resolvers,
                                 @NotNull List<CandidateInfo> container,
                                 @NotNull PsiElement place,
                                 PsiFile placeFile) {
    super(filter, container);
    myResolvers = resolvers;
    myName = name;
    myPlace = place;
    myPlaceFile = placeFile;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, ResolveState state) {
    if (myCachedResult != null && myCachedResult.length == 1 && myCachedResult[0].isAccessible()) {
      return false;
    }
    if (myName == null || PsiUtil.checkName(element, myName, myPlace)) {
      return super.execute(element, state);
    }
    return true;
  }

  @Override
  protected void add(PsiElement element, PsiSubstitutor substitutor) {
    add(new CandidateInfo(element, substitutor));
  }

  protected void add(CandidateInfo info) {
    myCachedResult = null;
    myResults.add(info);
  }

  @Override
  public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.CHANGE_LEVEL && myName != null) {
      getResult();
    }
  }

  @NotNull
  public JavaResolveResult[] getResult() {
    if (myCachedResult == null) {
      final List<CandidateInfo> conflicts = getResults();
      for (PsiConflictResolver resolver : myResolvers) {
        CandidateInfo candidate = resolver.resolveConflict(conflicts);
        if (candidate != null) {
          conflicts.clear();
          conflicts.add(candidate);
          break;
        }
      }
      myCachedResult = conflicts.toArray(new JavaResolveResult[conflicts.size()]);
    }

    return myCachedResult;
  }

  @Override
  public String getName(ResolveState state) {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == NameHint.KEY) {
      //noinspection unchecked
      return myName != null ? (T)this : null;
    }
    return super.getHint(hintKey);
  }
}
