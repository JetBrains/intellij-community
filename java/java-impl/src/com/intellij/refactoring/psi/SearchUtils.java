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
package com.intellij.refactoring.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;

public class SearchUtils{
    private SearchUtils(){
    }

    public static Iterable<PsiReference> findAllReferences(PsiElement element, SearchScope scope){

        return new ArrayIterable<>(ReferencesSearch.search(element, scope, true).toArray(PsiReference.EMPTY_ARRAY));
/*
        try {
            Class<?> searchClass = Class.forName("com.intellij.psi.search.searches.ReferencesSearch");

            final Method[] methods = searchClass.getMethods();
            for (Method method : methods) {
                if ("search".equals(method.getName()) &&) {
                    return (Iterable<PsiReference>) method.invoke(null, element, scope, true);
                }
            }
            return null;
        } catch (ClassNotFoundException ignore) {
            return null;
        } catch (IllegalAccessException ignore) {
            return null;
        } catch (InvocationTargetException ignore) {
            return null;
        }
        return ReferencesSearch.search(element, scope, true).findAll();
        */
    }

    public static Iterable<PsiReference> findAllReferences(PsiElement element){
        return findAllReferences(element, PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element));
    }

    public static Iterable<PsiMethod> findOverridingMethods(PsiMethod method){
        return new ArrayIterable<>(OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY));
       // return OverridingMethodsSearch.search(method, method.getUseScope(), true).findAll();
    }

    public static Iterable<PsiClass> findClassInheritors(PsiClass aClass, boolean deep){
        return new ArrayIterable<>(ClassInheritorsSearch.search(aClass, deep).toArray(PsiClass.EMPTY_ARRAY));
       // return ClassInheritorsSearch.search(aClass, deep);
    }

}

