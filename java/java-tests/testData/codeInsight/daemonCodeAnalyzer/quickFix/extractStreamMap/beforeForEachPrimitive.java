// "Extract variable 'i' to 'mapToInt' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void test2(List<String> list) {
    list.stream().forEach(s -> {
      int <caret>i = s.length();
      System.out.println(i);
    });
  }
}