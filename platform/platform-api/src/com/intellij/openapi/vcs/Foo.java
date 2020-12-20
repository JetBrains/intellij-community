// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

public class Foo {
  void f() {
    //java.util.function.Function<String, AbsList> f = s -> new IntArrayList();
  }
  IntList crea() {
    return new IntArrayList();
  }
  public static void main(String[] args) { }
}
class IntList { }
abstract class AbsList extends IntList {}
class IntArrayList extends AbsList { }