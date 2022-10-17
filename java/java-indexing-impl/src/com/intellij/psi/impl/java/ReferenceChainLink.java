// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.search.ApproximateResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

    GlobalSearchScope scope = ResolveScopeManager.getInstance(project).getDefaultResolveScope(placeFile);
    if (!isCall) {
      PsiPackage pkg = JavaPsiFacade.getInstance(project).findPackage(referenceName);
      if (pkg != null && pkg.getDirectories(scope).length > 0) return null;
    }

    Map<Pair<ReferenceChainLink, GlobalSearchScope>, List<PsiMember>> cache =
      CachedValuesManager.getManager(project).getCachedValue(project, () -> {
        Map<Pair<ReferenceChainLink, GlobalSearchScope>, List<PsiMember>> map = ConcurrentFactoryMap.createMap(
          pair -> pair.first.calcMembersUnlessTooMany(pair.second));
        return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      });
    List<PsiMember> candidates = cache.get(Pair.create(this, scope));
    if (candidates == null) {
      markExpensive(project);
      return null;
    }

    return ContainerUtil.filter(candidates, candidate -> canBeAccessible(placeFile, candidate));
  }

  @Nullable
  private List<PsiMember> calcMembersUnlessTooMany(@NotNull GlobalSearchScope scope) {
    List<PsiMember> candidates = new ArrayList<>();
    AtomicInteger count = new AtomicInteger();
    Processor<PsiMember> processor = member -> {
      if (!(member instanceof PsiMethod && !ApproximateResolver.canHaveArgCount((PsiMethod)member, argCount))) {
        candidates.add(member);
      }
      return count.incrementAndGet() < 42;
    };
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());
    if (isCall) {
      if (!cache.processMethodsWithName(referenceName, processor, scope, null)) {
        return null;
      }
    }
    else {
      if (!cache.processFieldsWithName(referenceName, processor, scope, null)) {
        return null;
      }
    }

    if (!cache.processClassesWithName(referenceName, processor, scope, null)) {
      return null;
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

  @NotNull
  List<? extends PsiMember> getSymbolMembers(@NotNull Set<? extends PsiClass> qualifiers) {
    return isCall ? ApproximateResolver.getPossibleMethods(qualifiers, referenceName, argCount)
                  : ApproximateResolver.getPossibleNonMethods(qualifiers, referenceName);
  }

}
