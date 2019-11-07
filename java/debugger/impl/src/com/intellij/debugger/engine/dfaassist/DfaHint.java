// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import org.jetbrains.annotations.NotNull;

enum DfaHint {
  NONE(null), ANY_VALUE(null, true), TRUE("= true", true), FALSE("= false", true), 
  NPE("[NullPointerException]"), NULL_AS_NOT_NULL("[Null passed where not-null expected]"), CCE("[ClassCastException]"),
  ASE("[ArrayStoreException]"), AIOOBE("[ArrayIndexOutOfBoundsException]"), FAIL("[Method will fail]", true);

  private final String myTitle;
  private final boolean myValue;

  DfaHint(String title) {
    this(title, false);
  }

  DfaHint(String title, boolean value) {
    myTitle = title;
    myValue = value;
  }

  String getTitle() {
    return myTitle;
  }

  @NotNull
  DfaHint merge(@NotNull DfaHint other) {
    if (other == this) return this;
    if (this.myValue && other.myValue) return ANY_VALUE;
    if (this.myValue) return other;
    if (other.myValue) return this;
    return NONE;
  }
}
