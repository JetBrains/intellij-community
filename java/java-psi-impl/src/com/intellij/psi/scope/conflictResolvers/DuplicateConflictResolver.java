/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 17:21:42
 * To change this template use Options | File Templates.
 */
public class DuplicateConflictResolver implements PsiConflictResolver{
  public static final DuplicateConflictResolver INSTANCE = new DuplicateConflictResolver();

  private DuplicateConflictResolver() {
  }

  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts){
    if (conflicts.size() == 1) return conflicts.get(0);
    final Map<Object, CandidateInfo> uniqueItems = new HashMap<Object, CandidateInfo>();
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
