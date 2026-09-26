package org.eclipse.jdt.annotation;

import java.lang.annotation.*;

@interface NonNullByDefault {}
@Target(ElementType.TYPE_USE)
@interface Nullable {}

@NonNullByDefault
class X {
  void test() {
    <selection>Y.getFoo()</selection>;
  }
}

class Y {
  static @Nullable String getFoo() { return null; } 
}