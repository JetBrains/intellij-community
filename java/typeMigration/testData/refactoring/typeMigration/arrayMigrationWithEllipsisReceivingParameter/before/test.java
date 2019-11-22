// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

class Test {

  int[] migrationField = new int[];

  public void foo() {
    Bar.of(migrationField);
  }
}

class Bar {
  public static Bar of(int... args) {
    return new Bar();
  }
}