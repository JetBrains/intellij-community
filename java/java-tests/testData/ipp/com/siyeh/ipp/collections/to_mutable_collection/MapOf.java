import java.util.*;

class Test {

  void foo() {
    process(/*1*/Map.of/*2*/(<caret>"foo"/*3*/, "bar", "goo", "baz")/*4*/);
  }

  void process(Map<String, String> model) {

  }
}