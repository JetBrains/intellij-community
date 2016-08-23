/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.search.ApproximateResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
public class ReferenceChainLink {
  final String referenceName;
  final boolean isCall;
  final int argCount;

  public ReferenceChainLink(@NotNull String referenceName, boolean isCall, int argCount) {
    this.referenceName = referenceName;
    this.isCall = isCall;
    this.argCount = argCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReferenceChainLink)) return false;

    ReferenceChainLink link = (ReferenceChainLink)o;

    if (isCall != link.isCall) return false;
    if (argCount != link.argCount) return false;
    if (!referenceName.equals(link.referenceName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = referenceName.hashCode();
    result = 31 * result + (isCall ? 1 : 0);
    result = 31 * result + argCount;
    return result;
  }

  @Override
  public String toString() {
    return referenceName + (isCall ? "(" + argCount + ")" : "");
  }

  private static final Key<Set<ReferenceChainLink>> EXPENSIVE_LINKS = Key.create("EXPENSIVE_CHAIN_LINKS");


  @Nullable
  List<PsiMember> getGlobalMembers(VirtualFile placeFile, Project project) {
    if (isExpensive(project)) return null;

    List<PsiMember> candidates = new ArrayList<>();
    AtomicInteger count = new AtomicInteger();
    Processor<PsiMember> processor = member -> {
      if (canBeAccessible(placeFile, member) && (!(member instanceof PsiMethod) ||
                                                 ApproximateResolver.canHaveArgCount((PsiMethod)member, argCount))) {
        candidates.add(member);
      }
      return count.incrementAndGet() < 42;
    };
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    GlobalSearchScope scope = ResolveScopeManager.getInstance(project).getDefaultResolveScope(placeFile);
    if (isCall) {
      if (!cache.processMethodsWithName(referenceName, processor, scope, null)) {
        markExpensive(project);
        return null;
      }
    }
    else {
      PsiPackage pkg = JavaPsiFacade.getInstance(project).findPackage(referenceName);
      if (pkg != null && pkg.getDirectories(scope).length > 0) return null;

      if (!cache.processFieldsWithName(referenceName, processor, scope, null) ||
          !cache.processClassesWithName(referenceName, processor, scope, null)) {
        markExpensive(project);
        return null;
      }
    }
    return candidates;
  }

  private static boolean canBeAccessible(VirtualFile placeFile, PsiMember member) {
    return !member.hasModifierProperty(PsiModifier.PRIVATE) || placeFile.equals(PsiUtilCore.getVirtualFile(member));
  }

  private boolean isExpensive(Project project) {
    Set<ReferenceChainLink> expensive = project.getUserData(EXPENSIVE_LINKS);
    return expensive != null && expensive.contains(this);
  }

  private void markExpensive(Project project) {
    Set<ReferenceChainLink> expensive = project.getUserData(EXPENSIVE_LINKS);
    if (expensive == null) {
      project.putUserData(EXPENSIVE_LINKS, expensive = ContainerUtil.newConcurrentSet());
    }
    expensive.add(this);
  }

  public List<? extends PsiMember> getSymbolMembers(Set<PsiClass> qualifiers) {
    return isCall ? ApproximateResolver.getPossibleMethods(qualifiers, referenceName, argCount)
                  : ApproximateResolver.getPossibleNonMethods(qualifiers, referenceName);
  }

}
