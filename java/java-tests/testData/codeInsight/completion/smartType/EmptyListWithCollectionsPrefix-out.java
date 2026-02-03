import java.util.Collections;

class Foo {
  java.util.List<String> foo() {
    return Collections.emptyList();<caret>
  }
}