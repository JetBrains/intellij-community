/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection.bytecodeAnalysis.data;

@SuppressWarnings("unused")
public final class TestHashCollision {
  // signature hashes for these two methods collide: MD5("()V"+"test11044") and MD5("()V"+"test20917") have the same prefix: 3d802c48
  void test11044() {
    // Though purity can be inferred for this method, due to collision we erase inference result
  }

  void test20917() {
    System.out.println("non-pure");
  }
}