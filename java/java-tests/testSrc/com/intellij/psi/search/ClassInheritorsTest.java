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
package com.intellij.psi.search;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.testFramework.IdeaTestUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ClassInheritorsTest extends DaemonAnalyzerTestCase {
  @Override
  protected Sdk getTestProjectJdk() {
    // there must be src.zip
    return IdeaTestUtil.getMockJdk17("1.7");
  }

  public void testClsAndSourcesDoNotMixUp() {
    PsiClass numberClass = getJavaFacade().findClass("java.lang.Number", GlobalSearchScope.allScope(getProject()));
    assertTrue(String.valueOf(numberClass), numberClass instanceof ClsClassImpl);
    PsiClass n2 = (PsiClass)numberClass.getNavigationElement();
    assertTrue(String.valueOf(n2), n2 instanceof PsiClassImpl);
    Collection<PsiClass> subClasses = DirectClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(getProject())).findAll();
    List<String> fqn = subClasses.stream().map(PsiClass::getQualifiedName).sorted().collect(Collectors.toList());
    assertEquals(fqn.toString(), fqn.size(), new HashSet<>(fqn).size()); // no dups mean no Cls/Psi mixed

    Collection<PsiClass> allSubClasses = ClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(getProject()), true).findAll();
    List<String> allFqn = allSubClasses.stream().map(PsiClass::getQualifiedName).sorted().collect(Collectors.toList());
    assertEquals(allFqn.toString(), allFqn.size(), new HashSet<>(allFqn).size());
  }
}
