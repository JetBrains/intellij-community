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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

class DiscoveredTestsTreeModel extends BaseTreeModel<Object> {
  private final Object myRoot = ObjectUtils.NULL;

  private final List<Node.Clazz> myTestClasses = new SmartList<>();
  private final Map<Node.Clazz, List<Node.Method>> myTests = new HashMap<>();

  @Override
  public synchronized List<?> getChildren(Object parent) {
    if (parent == myRoot) return getTestClasses();
    if (parent instanceof Node.Clazz) {
      //noinspection unchecked
      return ContainerUtil.newArrayList(myTests.get((Node.Clazz)parent));
    }
    return Collections.emptyList();
  }

  synchronized List<Node<PsiClass>> getTestClasses() {
    return ContainerUtil.newArrayList(myTestClasses);
  }

  @Override
  public Object getRoot() {
    return myRoot;
  }

  @Override
  public boolean isLeaf(Object object) {
    //TODO malenkov
    return myRoot != object && super.isLeaf(object);
  }

  synchronized void addTest(@NotNull PsiClass testClass, @NotNull PsiMethod testMethod, @Nullable String parameter) {
    Node.Clazz classNode = ReadAction.compute(() -> new Node.Clazz(testClass));
    Node.Method methodNode = ReadAction.compute(() -> new Node.Method(testMethod));

    int idx = ReadAction.compute(() -> Collections.binarySearch(myTestClasses,
                                                                classNode,
                                                                Comparator.comparing((Node.Clazz c) -> c.getName()).thenComparing(c -> c.getPackageName())));
    if (idx < 0) {
      int insertIdx = -idx - 1;
      myTestClasses.add(insertIdx, classNode);
      List<Node.Method> methods = new SmartList<>();
      methods.add(methodNode);
      myTests.put(classNode, methods);
      return;
    }

    List<Node.Method> testMethods = myTests.get(myTestClasses.get(idx));
    int methodIdx = ReadAction.compute(() -> Collections.binarySearch(testMethods,
                                                                      methodNode,
                                                                      (o1, o2) -> Comparing.compare(o1.getName(), o2.getName())));

    Node.Method actualMethodNode;
    if (methodIdx < 0) {
      methodIdx = -methodIdx - 1;
      testMethods.add(methodIdx, methodNode);
      actualMethodNode = methodNode;
    } else {
      actualMethodNode = testMethods.get(methodIdx);
    }
    if (parameter != null) {
      actualMethodNode.addParameter(parameter);
    }

    treeStructureChanged(null, null, null);
  }

  @NotNull
  synchronized TestMethodUsage[] getTestMethods() {
    //noinspection unchecked
    return myTests
      .entrySet()
      .stream()
      .flatMap(e -> e.getValue().stream().map(m -> new TestMethodUsage(m.getPointer(), e.getKey().getPointer(), m.getParameters())))
      .toArray(TestMethodUsage[]::new);
  }

  synchronized int getTestCount() {
    return myTests.values().stream().mapToInt(ms -> ms.size()).sum();
  }

  synchronized int getTestClassesCount() {
    return myTests.size();
  }

  public static abstract class Node<Psi extends PsiMember> {
    @NotNull
    private final SmartPsiElementPointer<Psi> myPointer;
    private final String myName;
    private final Icon myIcon;

    static class Clazz extends Node<PsiClass> {
      private final String myPackageName;

      Clazz(@NotNull PsiClass aClass) {
        super(aClass);
        myPackageName = PsiUtil.getPackageName(aClass);
      }

      String getPackageName() {
        return myPackageName;
      }
    }

    static class Method extends Node<PsiMethod> {
      private final Collection<String> myParameters = new THashSet<>();

      private Method(@NotNull PsiMethod method) {
        super(method);
      }

      public void addParameter(@NotNull String parameter) {
        myParameters.add(parameter);
      }

      @NotNull Collection<String> getParameters() {
        return myParameters;
      }
    }

    private Node(@NotNull Psi psi) {
      myPointer = SmartPointerManager.createPointer(psi);
      myName = psi.getName();
      myIcon = psi.getIcon(Iconable.ICON_FLAG_READ_STATUS);
    }

    @NotNull
    public SmartPsiElementPointer<Psi> getPointer() {
      return myPointer;
    }

    String getName() {
      return myName;
    }

    Icon getIcon() {
      return myIcon;
    }
  }
}
