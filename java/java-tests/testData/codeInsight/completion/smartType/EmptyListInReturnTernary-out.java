import java.util.Collections;

class Foo {
  java.util.Set<String> foo() {
    return true ? Collections.<String>emptySet() : <caret>
  }
}