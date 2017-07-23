// "Replace with forEach" "INFORMATION"
import java.util.*;

class A {
  void fun(List<String>... lists) {
    for (List<String> list : li<caret>sts) {
      list.add("");
    }
  }



}