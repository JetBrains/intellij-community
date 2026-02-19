// "Replace 'foo().equals(...)' with 'Objects.equals(foo(), ...)'" "true-preview"

import org.jetbrains.annotations.*;

class A{
  void test(Object bar) {
    if (foo().equa<caret>ls(bar)) {}
  }

  @Nullable Object foo();
}