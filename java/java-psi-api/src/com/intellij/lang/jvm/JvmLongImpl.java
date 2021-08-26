// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

public class JvmLongImpl implements JvmLong {
  private final long value;

  public JvmLongImpl(Long value) {
    this.value = value;
  }

  @Override
  public Long getValue() {
    return value;
  }
}