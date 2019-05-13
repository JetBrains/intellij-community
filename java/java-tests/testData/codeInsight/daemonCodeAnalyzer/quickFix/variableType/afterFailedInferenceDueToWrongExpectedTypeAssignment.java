// "Change variable 'foo' type to 'java.util.List<java.lang.String>'" "true"

import java.util.Arrays;
import java.util.List;

class MyClass {
  void bar() {
    List<String> foo;
    foo = (Arrays.asList("a"));
  }
}