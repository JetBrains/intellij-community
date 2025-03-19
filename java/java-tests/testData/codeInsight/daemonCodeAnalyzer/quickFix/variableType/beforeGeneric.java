// "Change variable 'box' type to 'A.Box<List<String>>'" "true-preview"
import java.util.*;

public class A {
  void test() {
    Box<Set<String>> box = new <caret>Box<>(Arrays.asList("foo"));
  }

  class Box<T> {
    Box(T value) {}
  }
}