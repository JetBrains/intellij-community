// "Replace '(random() ? foo() : bar()).equals(...)' with 'Objects.equals(random() ? foo() : bar(), ...)'" "true"

import org.jetbrains.annotations.*;

import java.util.Objects;

class A{
  void test(Object bar) {
    if (Objects.equals(random() ? foo() : bar(), bar)) {}
  }

  @Nullable Object foo();
  @Nullable Object bar();

  boolean random();
}