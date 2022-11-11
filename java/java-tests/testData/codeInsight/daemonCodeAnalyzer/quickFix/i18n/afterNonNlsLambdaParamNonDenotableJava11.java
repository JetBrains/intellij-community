// "Annotate parameter 'obj' as '@NonNls'" "true-preview"
import org.jetbrains.annotations.NonNls;

import java.util.Optional;

class Foo {
  void consumerTest() {
    Optional.of(new Object() {void foo(String s) {}}).ifPresent((@NonNls var obj) -> obj.foo("bar"));
  }
}
