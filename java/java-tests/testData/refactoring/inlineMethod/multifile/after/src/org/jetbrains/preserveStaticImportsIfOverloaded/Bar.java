package org.jetbrains.preserveStaticImportsIfOverloaded;

import static org.jetbrains.preserveStaticImportsIfOverloaded.Foo.foo;
public class Bar {
  void bar() {
      foo(1);
  }
}