// "Change type of 'e' to 'List<String>' and remove cast" "false"
import java.util.*;

class Test {
  void test(List<? extends List> list) {
    for(List<?> e : list) {
      List<String> strList = (List<caret><String>)e;
      System.out.println(strList.get(0).trim());
    }
  }
}