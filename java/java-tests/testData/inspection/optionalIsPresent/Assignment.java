// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.util.Optional;

class OptionalIsPresent {
  public void testIsPresent(Optional<String> str) {
    String val;
    if (<warning descr="Can be replaced with single expression in functional style">str.isPresent()</warning>) {
      val = str.get();
    } else {
      val = "";
    }
    System.out.println(val);
  }
}
