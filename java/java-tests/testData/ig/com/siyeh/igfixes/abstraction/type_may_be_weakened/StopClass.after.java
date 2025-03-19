// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igfixes.abstraction.type_may_be_weakened;

import java.util.ArrayList;
import java.util.List;

class B {
  void foo() {}
}

class Stop extends B {}

class A extends Stop { }


class Main {
  public static void main(String[] args) {
    Stop a = new A();
    a.foo();
  }
}