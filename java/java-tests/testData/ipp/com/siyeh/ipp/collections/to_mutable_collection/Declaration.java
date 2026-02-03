import java.util.*;

class Test {

  void foo() {
    Map<String, String> map = /*1*/(Collections.singletonMap/*2*/<caret>(/*3*/"foo", "bar"))/*4*/;
    process(map);
  }

  void process(Map<String, String> model) {

  }
}