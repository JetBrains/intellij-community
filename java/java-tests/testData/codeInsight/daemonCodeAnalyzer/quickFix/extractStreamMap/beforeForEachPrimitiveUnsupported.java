// "Extract variable 'i' to separate stream step" "false"
import java.util.*;
import java.util.stream.*;

public class Test {
  void test2(List<String> list) {
    list.stream().forEach(s -> {
      float <caret>i = s.length();
      System.out.println(i);
    });
  }
}