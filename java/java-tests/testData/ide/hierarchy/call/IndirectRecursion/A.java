// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class A {
  public static void main(String[] args) {
    recursive1();
  }
  public static void recursive1() {
    recursive2();
  }

  public static void recursive2() {
    recursive1();
  }
}
