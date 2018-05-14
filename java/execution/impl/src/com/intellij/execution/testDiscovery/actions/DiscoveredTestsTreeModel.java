// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

class DiscoveredTestsTreeModel extends BaseTreeModel<Object> {
  private final Object myRoot = ObjectUtils.NULL;

  private final List<Node<PsiClass>> myTestClasses = new SmartList<>();
  private final Map<Node<PsiClass>, List<Node<PsiMethod>>> myTests = new HashMap<>();

  @Override
  public synchronized List<?> getChildren(Object parent) {
    if (parent == myRoot) return getTestClasses();
    if (parent instanceof Node<?> && ((Node)parent).isClass()) {
      //noinspection unchecked
      return ContainerUtil.newArrayList(myTests.get((Node<PsiClass>)parent));
    }
    return Collections.emptyList();
  }

  synchronized List<Node<PsiClass>> getTestClasses() {
    return ContainerUtil.newArrayList(myTestClasses);
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
    Node<PsiClass> classNode = ReadAction.compute(() -> new Node<>(testClass));
    Node<PsiMethod> methodNode = ReadAction.compute(() -> new Node<>(testMethod));

    int idx = ReadAction.compute(() -> Collections.binarySearch(myTestClasses,
                                                                classNode,
                                                                Comparator.comparing((Node<PsiClass> c) -> c.getName()).thenComparing(c -> c.getPackageName())));
    if (idx < 0) {
      int insertIdx = -idx - 1;
      myTestClasses.add(insertIdx, classNode);
      List<Node<PsiMethod>> methods = new SmartList<>();
      methods.add(methodNode);
      myTests.put(classNode, methods);
      return;
    }

    List<Node<PsiMethod>> testMethods = myTests.get(myTestClasses.get(idx));
    int methodIdx = ReadAction.compute(() -> Collections.binarySearch(testMethods,
                                                                      methodNode,
                                                                      (o1, o2) -> Comparing.compare(o1.getName(), o2.getName())));
    if (methodIdx < 0) {
      methodIdx = -methodIdx - 1;
      testMethods.add(methodIdx, methodNode);
    }

    treeStructureChanged(null, null, null);
  }

  @NotNull
  synchronized DiscoveredTestsTreeModel.Node<PsiMethod>[] getTestMethods() {
    //noinspection unchecked
    return myTests
      .values()
      .stream()
      .flatMap(vs -> vs.stream())
      .toArray(DiscoveredTestsTreeModel.Node[]::new);
  }

  public synchronized int getTestCount() {
    return myTests.values().stream().mapToInt(ms -> ms.size()).sum();
  }

  static class Node<Psi extends PsiMember> {
    @NotNull
    private final SmartPsiElementPointer<Psi> myPointer;
    private final boolean myClass;
    private final String myName;
    private final String myPackageName;
    private final Icon myIcon;

    private Node(@NotNull Psi psi) {
      myPointer = SmartPointerManager.createPointer(psi);
      myName = psi.getName();
      myClass = psi instanceof PsiClass;
      myPackageName = myClass ? PsiUtil.getPackageName((PsiClass)psi) : null;
      myIcon = psi.getIcon(Iconable.ICON_FLAG_READ_STATUS);
    }

    @NotNull
    SmartPsiElementPointer<Psi> getPointer() {
      return myPointer;
    }

    boolean isClass() {
      return myClass;
    }

    String getName() {
      return myName;
    }

    String getPackageName() {
      return myPackageName;
    }

    Icon getIcon() {
      return myIcon;
    }
  }
}
