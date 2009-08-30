/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 21, 2006
 */
public class SourceUrlClassNamePair {
  private final String mySourceUrl;
  private final @Nullable String myClassName;

  public SourceUrlClassNamePair(String url, @Nullable String className) {
    mySourceUrl = url;
    myClassName = className;
  }

  public String getSourceUrl() {
    return mySourceUrl;
  }

  public @Nullable String getClassName() {
    return myClassName;
  }
}
