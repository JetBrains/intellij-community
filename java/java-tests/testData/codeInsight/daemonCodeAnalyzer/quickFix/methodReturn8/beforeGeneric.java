// "Make 'getList()' return 'A.Box<java.util.List<java.lang.String>>' or ancestor" "true-preview"
import java.util.*;

public class A {
  Box<Set<String>> getList() {
    return new <caret>Box<>(Arrays.asList("foo"));
  }

  class Box<T> {
    Box(T value) {}
  }
}