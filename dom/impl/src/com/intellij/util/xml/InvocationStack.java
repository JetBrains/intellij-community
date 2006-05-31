/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Condition;

import java.util.Stack;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.lang.reflect.Method;

/**
 * @author peter
 */
public class InvocationStack<T> {
  public static final InvocationStack<Object> INSTANCE = new InvocationStack<Object>();

  private final Map<Thread, Stack<Pair<JavaMethodSignature,T>>> myCallStacks = Collections.synchronizedMap(new WeakHashMap<Thread, Stack<Pair<JavaMethodSignature, T>>>());

  public final void push(Method method, T o) {
    JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    final Thread thread = Thread.currentThread();
    Stack<Pair<JavaMethodSignature, T>> stack = myCallStacks.get(thread);
    if (stack == null) {
      myCallStacks.put(thread, stack = new Stack<Pair<JavaMethodSignature, T>>());
    }
    stack.push(Pair.create(signature, o));
  }

  public final T findDeepestInvocation(Method method, Condition<T> stopAt) {
    final Stack<Pair<JavaMethodSignature, T>> stack = myCallStacks.get(Thread.currentThread());

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
    return myCallStacks.get(Thread.currentThread()).pop();
  }
}
