// "Change variable 'foo' type to 'List<String>'" "true"

import java.util.Arrays;
import java.util.List;

class MyClass {
  void bar() {
    List<String> foo = Arrays.asList("a");
  }
}