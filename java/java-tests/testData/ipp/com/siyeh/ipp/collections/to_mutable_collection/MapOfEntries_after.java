import java.util.*;

class Test {

  void foo() {
    Map.Entry<String, String> entry = Map.entry("foo", "bar");
      Map<String, String> model = new HashMap<>();
      model.put(entry.getKey(), entry.getValue());
      model.put("goo", "baz");/*2*//*3*/
      process(/*1*/model/*4*/);
  }

  void process(Map<String, String> model) {

  }
}