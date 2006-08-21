/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.javaee;

/**
 * @author Gregory.Shrago
 */
public interface PairProcessor<S, T> {
  boolean process(S s, T t);
}
