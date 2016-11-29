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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.stubsHierarchy.ClassHierarchy;
import com.intellij.psi.stubsHierarchy.HierarchyService;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class StubHierarchyInheritorSearcher extends QueryExecutorBase<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.StubHierarchyInheritorSearcher");
  public StubHierarchyInheritorSearcher() {
    super(true);
  }

  private static boolean isSearching() {
    return Registry.is("java.use.stub.hierarchy.in.inheritor.search");
  }

  @NotNull
  public static GlobalSearchScope restrictScope(@NotNull GlobalSearchScope scope) {
    if (!isSearching()) return scope;

    Project project = scope.getProject();
    return project == null ? scope : HierarchyService.getHierarchy(project).restrictToUncovered(scope);
  }

  @Override
  public void processQuery(@NotNull DirectClassInheritorsSearch.SearchParameters p, @NotNull Processor<PsiClass> consumer) {
    if (!(p.getScope() instanceof GlobalSearchScope) || !isSearching()) return;

    PsiClass base = p.getClassToProcess();
    PsiElement original = base.getOriginalElement();
    if (original instanceof PsiClass) {
      base = (PsiClass)original;
    }

    GlobalSearchScope scope = (GlobalSearchScope)p.getScope();
    ClassHierarchy hierarchy = HierarchyService.getHierarchy(base.getProject());
    for (SmartClassAnchor anchor : hierarchy.getDirectSubtypeCandidates(base)) {
      if ((p.includeAnonymous() || !hierarchy.isAnonymous(anchor)) && !processCandidate(consumer, base, scope, hierarchy, anchor)) {
        return;
      }
    }
  }

  private static boolean processCandidate(Processor<PsiClass> consumer,
                                          PsiClass base,
                                          GlobalSearchScope scope,
                                          ClassHierarchy hierarchy, SmartClassAnchor anchor) {
    VirtualFile file = anchor.retrieveFile();
    if (!scope.contains(file)) return true;

    PsiClass candidate = anchor.retrieveClass(base.getProject());
    if (!PsiSearchScopeUtil.isInScope(candidate.getResolveScope(), base)) return true;

    if (hierarchy.hasAmbiguousSupers(anchor) && !candidate.isInheritor(base, false)) return true;

    return consumer.process(candidate);
  }
}
