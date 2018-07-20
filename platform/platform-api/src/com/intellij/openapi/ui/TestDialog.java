// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

public interface TestDialog {
  TestDialog DEFAULT = new TestDialog() {
    public int show(String message) {
      throw new RuntimeException(message);
    }
  };
  TestDialog OK = new TestDialog() {
    @Override
    public int show(String message) {
      return 0;
    }
  };
  TestDialog NO = new TestDialog() {
    public int show(String message) {
      return Messages.NO;
    }
  };

  int show(String message);
}
