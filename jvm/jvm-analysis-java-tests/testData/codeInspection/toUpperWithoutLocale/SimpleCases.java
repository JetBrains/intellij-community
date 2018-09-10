// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import java.util.List;
import java.util.Locale;

public class SimpleCases {
  String s1 = "123".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
  String s2 = "123".toUpperCase(Locale.ENGLISH);

  public void foo() {
    String foo = "foo".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
    String bar = "bar".toLowerCase(Locale.US);
  }

  public void methodRef(List<String> list) {
    list.stream().map(String::<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>);
  }
}
