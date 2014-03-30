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
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefaultSymbolNavigationContributor implements ChooseByNameContributorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.DefaultSymbolNavigationContributor");

  @Override
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    HashSet<String> set = new HashSet<String>();
    cache.getAllMethodNames(set);
    cache.getAllFieldNames(set);
    cache.getAllClassNames(set);
    return ArrayUtil.toStringArray(set);
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

    List<PsiMember> result = new ArrayList<PsiMember>();
    for (PsiMethod method : cache.getMethodsByName(name, scope)) {
      if (!method.isConstructor() && isOpenable(method) && !hasSuperMethod(method)) {
        result.add(method);
      }
    }
    for (PsiField field : cache.getFieldsByName(name, scope)) {
      if (isOpenable(field)) {
        result.add(field);
      }
    }
    for (PsiClass aClass : cache.getClassesByName(name, scope)) {
      if (isOpenable(aClass)) {
        result.add(aClass);
      }
    }
    PsiMember[] array = result.toArray(new PsiMember[result.size()]);
    Arrays.sort(array, MyComparator.INSTANCE);
    return array;
  }

  private static boolean isOpenable(PsiMember member) {
    return member.getContainingFile().getVirtualFile() != null;
  }

  private static boolean hasSuperMethod(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;

    for (PsiMethod candidate : containingClass.findMethodsByName(method.getName(), true)) {
      if (candidate.getContainingClass() != containingClass && PsiSuperMethodImplUtil.isSuperMethodSmart(method, candidate)) {
        return true;
      }
    }
    return false;
  }

  public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());
    cache.processAllClassNames(processor, scope, filter);
    cache.processAllFieldNames(processor, scope, filter);
    cache.processAllMethodNames(processor, scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull final Processor<NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {

    GlobalSearchScope scope = parameters.getSearchScope();
    IdFilter filter = parameters.getIdFilter();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(scope.getProject());
    //noinspection UnusedDeclaration
    final Set<PsiMethod> collectedMethods = new THashSet<PsiMethod>();
    boolean success = cache.processFieldsWithName(name, new Processor<PsiField>() {
      @Override
      public boolean process(PsiField field) {
        if (isOpenable(field)) return processor.process(field);
        return true;
      }
    }, scope, filter) &&
                    cache.processClassesWithName(name, new Processor<PsiClass>() {
                      @Override
                      public boolean process(PsiClass aClass) {
                        if (isOpenable(aClass)) return processor.process(aClass);
                        return true;
                      }
                    }, scope, filter) &&
                    cache.processMethodsWithName(name, new Processor<PsiMethod>() {
                      @Override
                      public boolean process(PsiMethod method) {
                        if(!method.isConstructor() && isOpenable(method)) {
                          collectedMethods.add(method);
                        }
                        return true;
                      }
                    }, scope, filter);
    if (success) {
      // hashSuperMethod accesses index and can not be invoked without risk of the deadlock in processMethodsWithName
      Iterator<PsiMethod> iterator = collectedMethods.iterator();
      while(iterator.hasNext()) {
        PsiMethod method = iterator.next();
        if (!hasSuperMethod(method) && !processor.process(method)) return;
        ProgressManager.checkCanceled();
        iterator.remove();
      }
    }
  }

  private static class MyComparator implements Comparator<PsiModifierListOwner>{
    public static final MyComparator INSTANCE = new MyComparator();

    private final DefaultPsiElementCellRenderer myRenderer = new DefaultPsiElementCellRenderer();

    @Override
    public int compare(PsiModifierListOwner element1, PsiModifierListOwner element2) {
      if (element1 == element2) return 0;

      PsiModifierList modifierList1 = element1.getModifierList();
      PsiModifierList modifierList2 = element2.getModifierList();

      int level1 = modifierList1 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList1);
      int level2 = modifierList2 == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList2);
      if (level1 != level2) return level2 - level1;

      int kind1 = getElementTypeLevel(element1);
      int kind2 = getElementTypeLevel(element2);
      if (kind1 != kind2) return kind1 - kind2;

      if (element1 instanceof PsiMethod){
        LOG.assertTrue(element2 instanceof PsiMethod);
        PsiParameter[] parms1 = ((PsiMethod)element1).getParameterList().getParameters();
        PsiParameter[] parms2 = ((PsiMethod)element2).getParameterList().getParameters();

        if (parms1.length != parms2.length) return parms1.length - parms2.length;
      }

      String text1 = myRenderer.getElementText(element1);
      String text2 = myRenderer.getElementText(element2);
      if (!text1.equals(text2)) return text1.compareTo(text2);

      String containerText1 = myRenderer.getContainerText(element1, text1);
      String containerText2 = myRenderer.getContainerText(element2, text2);
      if (containerText1 == null) containerText1 = "";
      if (containerText2 == null) containerText2 = "";
      return containerText1.compareTo(containerText2);
    }

    private static int getElementTypeLevel(PsiElement element){
      if (element instanceof PsiMethod){
        return 1;
      }
      else if (element instanceof PsiField){
        return 2;
      }
      else if (element instanceof PsiClass){
        return 3;
      }
      else{
        LOG.error(element);
        return 0;
      }
    }
  }

}
