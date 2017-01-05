// "Replace with forEach" "false"
import java.util.*;

class A {
  void fun(List<String>... lists) {
    for (List<String> list : li<caret>sts) {
      System.out.println(list);
    }
  }



}