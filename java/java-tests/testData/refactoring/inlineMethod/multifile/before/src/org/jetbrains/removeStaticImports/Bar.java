package org.jetbrains.removeStaticImports;

import static org.jetbrains.removeStaticImports.Foo.foo;
public class Bar {
  void bar() {
    foo();
  }
}