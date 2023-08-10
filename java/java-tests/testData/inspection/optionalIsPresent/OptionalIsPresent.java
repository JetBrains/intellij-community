// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.lang.invoke.MethodHandle;

import java.util.Optional;

public class OptionalIsPresent {
  public void testIsPresent(Optional<String> str) {
    String val;
    if (<warning descr="Can be replaced with single expression in functional style">str.isPresent()</warning>) {
      val = str.get();
    } else {
      val = "";
    }
    System.out.println(val);
  }

  public void testOptional(Optional<String> str) {
    String val;
    if (<warning descr="Can be replaced with single expression in functional style">str.isEmpty()</warning>) {
      val = "";
    } else {
      val = str.get();
    }
    System.out.println(val);
  }
}