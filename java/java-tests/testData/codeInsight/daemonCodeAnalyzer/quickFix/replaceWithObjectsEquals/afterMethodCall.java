// "Replace 'foo().equals(...)' with 'Objects.equals(foo(), ...)'" "true"

import org.jetbrains.annotations.*;

import java.util.Objects;

class A{
  void test(Object bar) {
    if (Objects.equals(foo(), bar)) {}
  }

  @Nullable Object foo();
}