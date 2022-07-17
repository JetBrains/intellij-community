package p;

import org.checkerframework.checker.nullness.qual.Nullable;

class Test {
  @org.checkerframework.checker.nullness.qual.Nullable
  String s;
  
  {
      @Nullable String <caret>m = s;
  }
}