/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;

import java.lang.reflect.Method;
import java.util.Stack;

/**
 * @author peter
 */
public class InvocationStack<T> {
  public static final InvocationStack<Object> INSTANCE = new InvocationStack<Object>();

  private final ThreadLocal<Stack<Pair<JavaMethodSignature,T>>> myCallStacks = new ThreadLocal<Stack<Pair<JavaMethodSignature, T>>>();

  public final void push(Method method, T o) {
    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    Stack<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();
    if (stack == null) {
      myCallStacks.set(stack = new Stack<Pair<JavaMethodSignature, T>>());
    }
    stack.push(Pair.create(signature, o));
  }
                                                 
  public final T findDeepestInvocation(Method method, Condition<T> stopAt) {
    final Stack<Pair<JavaMethodSignature, T>> stack = myCallStacks.get();

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
    return myCallStacks.get().pop();
  }
}
