package org.eclipse.jdt.annotation;

import java.lang.annotation.*;

@interface NonNullByDefault {}
@Target(ElementType.TYPE_USE)
@interface Nullable {}

@NonNullByDefault
class X {
  void test() {
      @Nullable String x = Y.getFoo();
  }
}

class Y {
  static @Nullable String getFoo() { return null; } 
}