// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This class is out of scope
class Parent<T> {
  void foo(T fooValue) {}
}

class Test extends Parent<Long> {

  private Long migrationField;

  void main() {
    this.foo(migrationField);
  }
}