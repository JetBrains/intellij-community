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
package com.intellij.refactoring.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.containers.Stack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MethodInheritanceUtils {
    private MethodInheritanceUtils() {
        super();
    }

    public static Set<PsiMethod> calculateSiblingMethods(PsiMethod method) {
        final Set<PsiMethod> siblingMethods = new HashSet<>();
        final Stack<PsiMethod> pendingMethods = new Stack<>();
        pendingMethods.add(method);
        while(!pendingMethods.isEmpty())
        {
            final PsiMethod methodToAnalyze = pendingMethods.pop();
            siblingMethods.add(methodToAnalyze);
          final Iterable<PsiMethod> overridingMethods = OverridingMethodsSearch.search(methodToAnalyze, false);
            for (PsiMethod overridingMethod : overridingMethods) {
                if (!siblingMethods.contains(overridingMethod) &&
                        !pendingMethods.contains(overridingMethod)) {
                    pendingMethods.add(overridingMethod);
                }
            }
            final PsiMethod[] superMethods = methodToAnalyze.findSuperMethods();
            for (PsiMethod superMethod : superMethods) {
                if (!siblingMethods.contains(superMethod) &&
                        !pendingMethods.contains(superMethod)) {
                    pendingMethods.add(superMethod);
                }
            }
        }
        return siblingMethods;
    }

    public static boolean hasSiblingMethods(PsiMethod method) {


        final Iterable<PsiMethod> overridingMethods =
                SearchUtils.findOverridingMethods(method);
        if(overridingMethods.iterator().hasNext())
        {
            return true;
        }
        final PsiMethod[] superMethods = method.findSuperMethods();
        return superMethods.length!=0;

    }

    public static PsiClass[] findAvailableSuperClassesForMethod(PsiMethod method){
        final List<PsiClass> sourceClasses = new ArrayList<>();
        findAvailableSuperClasses(method, sourceClasses);
        return sourceClasses.toArray(new PsiClass[sourceClasses.size()]);
    }

    private static void findAvailableSuperClasses(PsiMethod method, List<PsiClass> sourceClasses){
        final PsiMethod[] superMethods = method.findSuperMethods(true);
        for(PsiMethod superMethod : superMethods){
            final PsiClass containingClass = superMethod.getContainingClass();
            if(!(containingClass instanceof PsiCompiledElement)){
                sourceClasses.add(containingClass);
                findAvailableSuperClasses(superMethod, sourceClasses);
            }
        }
    }

    public static PsiClass[] findAvailableSubClassesForMethod(PsiMethod method){
        final Iterable<PsiMethod> query = SearchUtils.findOverridingMethods(method);
        final List<PsiClass> sourceClasses = new ArrayList<>();
        for(PsiMethod superMethod : query){
            final PsiClass containingClass = superMethod.getContainingClass();
            if(!(containingClass instanceof PsiCompiledElement)){
                sourceClasses.add(containingClass);
            }
        }
        return sourceClasses.toArray(new PsiClass[sourceClasses.size()]);
    }

    public static PsiClass[] getNonLibrarySuperClasses(PsiClass sourceClass){

        final List<PsiClass> out = new ArrayList<>();
        findNonLibrarySupers(sourceClass, out);
        return out.toArray(new PsiClass[out.size()]);
    }

    private static void findNonLibrarySupers(PsiClass sourceClass, List<PsiClass> out){
        final PsiClass[] supers = sourceClass.getSupers();
        for(PsiClass psiClass : supers){
            if(!(psiClass instanceof PsiCompiledElement) && !out.contains(psiClass))
            {
                out.add(psiClass);
                findNonLibrarySupers(psiClass, out);
            }
        }
    }

    public static PsiClass[] getNonLibrarySubClasses(PsiClass sourceClass){
        final List<PsiClass> out = new ArrayList<>();
        final Iterable<PsiClass> query = SearchUtils.findClassInheritors(sourceClass, true);
        for(PsiClass psiClass : query){
            if(!(psiClass instanceof PsiCompiledElement))
            {
                out.add(psiClass);
            }
        }
        return out.toArray(new PsiClass[out.size()]);
    }

    
}
