package org.eclipse.jdt.annotation;

import java.lang.annotation.*;

@interface NonNullByDefault {}
@Target(ElementType.TYPE_USE)
@interface NonNull {}

@NonNullByDefault
class X {
  void test() {
    <selection>Y.getFoo()</selection>;
  }
}

class Y {
  static @NonNull String getFoo() { return null; } 
}