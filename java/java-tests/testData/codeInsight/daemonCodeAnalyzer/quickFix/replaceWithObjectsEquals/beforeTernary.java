// "Replace '(random() ? foo() : bar()).equals(...)' with 'Objects.equals(random() ? foo() : bar(), ...)'" "true-preview"

import org.jetbrains.annotations.*;

class A{
  void test(Object bar) {
    if ((random() ? foo() : bar()).equa<caret>ls(bar)) {}
  }

  @Nullable Object foo();
  @Nullable Object bar();

  boolean random();
}