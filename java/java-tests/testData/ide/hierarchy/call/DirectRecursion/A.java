// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class A {
  public static void main(String[] args) {
    recursive();
  }
  public static void recursive() {
    recursive();
  }
}
