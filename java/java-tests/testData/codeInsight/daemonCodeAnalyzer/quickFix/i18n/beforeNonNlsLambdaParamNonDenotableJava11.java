// "Annotate parameter 'obj' as '@NonNls'" "true-preview"
import java.util.Optional;

class Foo {
  void consumerTest() {
    Optional.of(new Object() {void foo(String s) {}}).ifPresent(obj -> obj.foo("<caret>bar"));
  }
}
