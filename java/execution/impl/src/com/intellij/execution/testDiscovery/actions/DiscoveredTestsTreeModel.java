// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

final class DiscoveredTestsTreeModel extends BaseTreeModel<Object> implements InvokerSupplier {
  private final Invoker myInvoker = Invoker.forBackgroundThreadWithReadAction(this);

  private final Object myRoot = ObjectUtils.NULL;

  private final List<Node.Clazz> myTestClasses = new SmartList<>();
  private final Map<Node.Clazz, List<Node.Method>> myTests = new HashMap<>();

  @Override
  public synchronized List<?> getChildren(Object parent) {
    if (parent == myRoot) return getTestClasses();
    if (parent instanceof Node.Clazz) {
      return new ArrayList<>(myTests.get((Node.Clazz)parent));
    }
    return Collections.emptyList();
  }

  synchronized List<Node<PsiClass>> getTestClasses() {
    return new ArrayList<>(myTestClasses);
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

  synchronized void addTest(@NotNull PsiClass testClass, @Nullable PsiMethod testMethod, @Nullable String parameter) {
    Node.Clazz classNode = ReadAction.compute(() -> new Node.Clazz(testClass));
    Node.Method methodNode = testMethod == null
                             ? null
                             : ReadAction.compute(() -> new Node.Method(testMethod));

    int idx = ReadAction.compute(() -> Collections.binarySearch(myTestClasses,
                                                                classNode,
                                                                Comparator.comparing((Node.Clazz c) -> c.getName()).thenComparing(c -> c.getPackageName())));
    if (idx < 0) {
      int insertIdx = -idx - 1;
      myTestClasses.add(insertIdx, classNode);
      myTests.put(classNode, methodNode == null
                             ? new SmartList<>()
                             : new SmartList<>(methodNode));

      treeStructureChanged(null, null, null);
      return;
    }

    List<Node.Method> testMethods = myTests.get(myTestClasses.get(idx));
    int methodIdx = methodNode != null ? ReadAction.compute(() -> Collections.binarySearch(testMethods, methodNode, (o1, o2) -> Comparing.compare(o1.getName(), o2.getName())))
                                       : -1;

    Node.Method actualMethodNode;
    if (methodIdx < 0) {
      methodIdx = -methodIdx - 1;
      if (methodNode != null) {
        testMethods.add(methodIdx, methodNode);
      }
      actualMethodNode = methodNode;
    }
    else {
      actualMethodNode = testMethods.get(methodIdx);
    }

    if (actualMethodNode != null && parameter != null) {
      actualMethodNode.addParameter(parameter);
    }

    treeStructureChanged(null, null, null);
  }

  @Override
  public @NotNull Invoker getInvoker() {
    return myInvoker;
  }

  // TODO: this method can be replaced with ClassUtil.getBinaryClassName so it handles local classes.
  public static @Nullable String getClassName(@NotNull PsiClass c) {
    if (c instanceof PsiAnonymousClass) {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(c, PsiClass.class);
      if (containingClass != null) {
        return ClassUtil.getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)c);
      }
    }
    return ClassUtil.getJVMClassName(c);
  }

  synchronized TestMethodUsage @NotNull [] getTestMethods() {
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

  public abstract static class Node<Psi extends PsiMember> {
    private final @NotNull SmartPsiElementPointer<Psi> myPointer;
    private final String myName;
    private final Icon myIcon;

    static class Clazz extends Node<PsiClass> {
      private final String myPackageName;

      Clazz(@NotNull PsiClass psi) {
        super(psi, o -> StringUtil.notNullize(o.getName(), StringUtil.notNullize(getClassName(o), "<null>")));
        myPackageName = PsiUtil.getPackageName(psi);
      }

      @NlsSafe String getPackageName() {
        return myPackageName;
      }
    }

    static final class Method extends Node<PsiMethod> {
      private final Collection<String> myParameters = new HashSet<>();

      private Method(@NotNull PsiMethod method) {
        super(method);
      }

      public void addParameter(@NotNull String parameter) {
        myParameters.add(parameter);
      }

      @NotNull
      Collection<String> getParameters() {
        return myParameters;
      }
    }

    private Node(@NotNull Psi psi) {
      this(psi, o -> o.getName());
    }

    public Node(@NotNull Psi psi, Function<? super Psi, String> calcName) {
      myPointer = SmartPointerManager.createPointer(psi);
      myName = calcName.fun(psi);
      myIcon = psi.getIcon(Iconable.ICON_FLAG_READ_STATUS);
    }

    public @NotNull SmartPsiElementPointer<Psi> getPointer() {
      return myPointer;
    }

    @NlsSafe String getName() {
      return myName;
    }

    Icon getIcon() {
      return myIcon;
    }
  }
}
