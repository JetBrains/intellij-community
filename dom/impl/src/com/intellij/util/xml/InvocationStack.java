/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class InvocationStack<T> {
  public static final InvocationStack<Object> INSTANCE = new InvocationStack<Object>();

  private final ThreadLocal<List<Pair<JavaMethodSignature,T>>> myCallStacks = new ThreadLocal<List<Pair<JavaMethodSignature, T>>>();

  public final void push(Method method, T o) {
    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    List<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();
    if (stack == null) {
      myCallStacks.set(stack = new ArrayList<Pair<JavaMethodSignature, T>>());
    }
    stack.add(Pair.create(signature, o));
  }
                                                 
  @Nullable
  public final T findDeepestInvocation(Method method, Condition<T> stopAt) {
    final List<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();

    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    for (int i = stack.size() - 2; i >= 0; i--) {
      final Pair<JavaMethodSignature, T> pair = stack.get(i);
      if (stopAt.value(pair.second)) {
        return stack.get(i + 1).second;
      }
      if (pair.first != signature) {
        return null;
      }
    }
    return stack.isEmpty() ? null : stack.get(0).second;
  }

  public final Pair<JavaMethodSignature,T> pop() {
    final List<Pair<JavaMethodSignature, T>> list = myCallStacks.get();
    return list.remove(list.size() - 1);
  }
}
