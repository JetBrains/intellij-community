package org.jetbrains.removeStaticImportsIfOverloadedUnused;

import static org.jetbrains.removeStaticImportsIfOverloadedUnused.Foo.foo;
public class Bar {
  void bar() {
    foo();
  }
}