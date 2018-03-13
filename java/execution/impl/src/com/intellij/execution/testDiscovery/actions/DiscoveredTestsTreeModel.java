// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class DiscoveredTestsTreeModel extends BaseTreeModel<Object> {
  private final Object myRoot = ObjectUtils.NULL;

  private final List<PsiClass> myTestClasses = new SmartList<>();
  private final Map<PsiClass, List<PsiMethod>> myTests = new HashMap<>();

  @Override
  public synchronized List<?> getChildren(Object parent) {
    if (parent == myRoot) return myTestClasses;
    if (parent instanceof PsiClass) {
      return myTests.get((PsiClass)parent);
    }
    return Collections.emptyList();
  }

  public synchronized List<PsiClass> getTestClasses() {
    return myTestClasses;
  }

  @Override
  public synchronized Object getRoot() {
    return myRoot;
  }


  @Override
  public boolean isLeaf(Object object) {
    //TODO malenkov
    return myRoot != object && super.isLeaf(object);
  }

  public synchronized void addTest(@NotNull PsiClass testClass, @NotNull PsiMethod testMethod) {
    int idx = ReadAction.compute(() -> Collections.binarySearch(myTestClasses,
                                                              testClass,
                                                              Comparator.comparing((PsiClass c) -> c.getName()).thenComparing(c -> c.getQualifiedName())));
    if (idx < 0) {
      int insertIdx = -idx - 1;
      myTestClasses.add(insertIdx, testClass);
      List<PsiMethod> methods = new SmartList<>();
      methods.add(testMethod);
      myTests.put(testClass, methods);
    }

    List<PsiMethod> testMethods = myTests.get(testClass);
    int methodIdx = ReadAction.compute(() -> Collections.binarySearch(testMethods,
                                                                      testMethod,
                                                                      (o1, o2) -> Comparing.compare(o1.getName(), o2.getName())));
    if (methodIdx < 0) {
      methodIdx = -methodIdx - 1;
      testMethods.add(methodIdx, testMethod);
    }

    treeStructureChanged(null, null, null);
  }

  public synchronized PsiMethod[] getTestMethods() {
    return myTests.values().stream().flatMap(vs -> vs.stream()).toArray(PsiMethod[]::new);
  }

  public synchronized int getTestCount() {
    return myTests.values().stream().mapToInt(ms -> ms.size()).sum();
  }
}
