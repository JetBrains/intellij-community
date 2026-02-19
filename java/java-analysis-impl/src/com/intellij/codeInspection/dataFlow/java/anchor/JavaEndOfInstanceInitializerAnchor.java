// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.anchor;

/**
 * An anchor that depicts end of instance initializer (pushed value is irrelevant).
 * Could be used to get the initial memory state before constructor processing. 
 */
public class JavaEndOfInstanceInitializerAnchor extends JavaDfaAnchor {
  @Override
  public String toString() {
    return "End of instance initializer";
  }
}
