/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 21, 2006
 */
public class SourceUrlClassNamePair {
  private final String[] mySourceUrl;
  private final @Nullable String[] myClassName;

  public SourceUrlClassNamePair(StringInterner interner, String url, @Nullable String className) {
    mySourceUrl = InternedPath.convert(interner, url, '/');
    myClassName = (className != null) ? InternedPath.convert(interner, className, '.') : null;
  }

  public String getSourceUrl() {
    return InternedPath.join(mySourceUrl, '/');
  }

  public @Nullable String getClassName() {
    return (myClassName != null) ? InternedPath.join(myClassName, '.') : null;
  }
}
