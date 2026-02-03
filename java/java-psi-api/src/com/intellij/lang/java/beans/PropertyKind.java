// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.beans;

public enum PropertyKind {
  GETTER("get"),
  BOOLEAN_GETTER("is"),
  SETTER("set");

  public final String prefix;

  PropertyKind(String prefix) {
    this.prefix = prefix;
  }
}
