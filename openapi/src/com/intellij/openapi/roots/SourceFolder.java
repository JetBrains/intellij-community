/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;




/**
 *  @author dsl
 */
public interface SourceFolder extends ContentFolder{

  /**
   * @return true if this <code>SourcePath</code> is a test source.
   */
  boolean isTestSource();
  String getPackagePrefix();
  void setPackagePrefix(String packagePrefix);
}
