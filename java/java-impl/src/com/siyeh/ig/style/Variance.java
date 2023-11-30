// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import org.jetbrains.annotations.NotNull;

enum Variance {
  NOVARIANT,// none
  COVARIANT, // return type only
  CONTRAVARIANT, // method params only
  INVARIANT; // both

  @NotNull
  Variance combine(@NotNull Variance other) {
    return Variance.values()[ordinal() | other.ordinal()];
  }

  static {
    // otherwise bitmasks in combine() won't work
    assert NOVARIANT.ordinal() == 0;
    assert COVARIANT.ordinal() == 1;
    assert CONTRAVARIANT.ordinal() == 2;
    assert INVARIANT.ordinal() == 3;
  }
}
