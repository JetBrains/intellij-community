import java.util.*;

class Test {
  void test() {
    List<String> list = /*1*/Arrays/*2*/./*3*/as<caret>List/*4*/("foo", "bar");
    List<String> list2 = /*1*/Arrays/*2*/./*3*/asList/*4*/("baz", "qux");
  }
}