import java.util.*;

class Test {

  void foo() {
      Map<String, String> model = new HashMap<>();
      model.put("foo", "bar");
      model.put("goo", "baz");/*2*//*3*/
      process(/*1*/model/*4*/);
  }

  void process(Map<String, String> model) {

  }
}