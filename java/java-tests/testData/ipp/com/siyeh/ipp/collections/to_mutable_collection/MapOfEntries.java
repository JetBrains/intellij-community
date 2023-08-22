import java.util.*;

class Test {

  void foo() {
    Map.Entry<String, String> entry = Map.entry("foo", "bar");
    process(/*1*/Map.ofEntries/*2*/(<caret>entry, Map.entry(/*3*/"goo", "baz"))/*4*/);
  }

  void process(Map<String, String> model) {

  }
}