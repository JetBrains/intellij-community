/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.util.containers.StringInterner;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 19, 2006
 */
public class TreeBasedPathsSet {
  private final TreeBasedMap<Object> myMap;

  public TreeBasedPathsSet(StringInterner interner, char separator) {
    myMap = new TreeBasedMap<Object>(interner, separator);
  }

  public void add(String path) {
    myMap.put(path, null);
  }

  public void remove(String path) {
    myMap.remove(path);
  }

  public boolean contains(String path) {
    return myMap.containsKey(path);
  }
}
