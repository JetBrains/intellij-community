// "Change variable 'foo' type to 'List<String>'" "true-preview"

import java.util.Arrays;
import java.util.List;

class MyClass {
  void bar() {
    List<String> foo = Arrays.asList("a");
  }
}