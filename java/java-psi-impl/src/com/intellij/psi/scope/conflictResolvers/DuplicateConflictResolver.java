// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtilCore;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class DuplicateConflictResolver implements PsiConflictResolver{
  public static final DuplicateConflictResolver INSTANCE = new DuplicateConflictResolver();

  private DuplicateConflictResolver() {
  }

  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts){
    if (conflicts.size() == 1) return conflicts.get(0);
    final Map<Object, CandidateInfo> uniqueItems = new HashMap<>();
    for (CandidateInfo info : conflicts) {
      final PsiElement element = info.getElement();
      Object key;
      if (info instanceof MethodCandidateInfo) {
        key = ((PsiMethod)element).getSignature(((MethodCandidateInfo)info).getSubstitutor(false));
      }
      else {
        key = PsiUtilCore.getName(element);
      }

      if (!uniqueItems.containsKey(key)) {
        uniqueItems.put(key, info);
      }
    }

    if(uniqueItems.size() == 1) return uniqueItems.values().iterator().next();
    return null;
  }

}
