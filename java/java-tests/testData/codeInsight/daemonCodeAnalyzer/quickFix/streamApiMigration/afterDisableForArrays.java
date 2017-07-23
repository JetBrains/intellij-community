// "Replace with forEach" "INFORMATION"
import java.util.*;

class A {
  void fun(List<String>... lists) {
      Arrays.stream(lists).forEach(list -> list.add(""));
  }



}