// "Extract variable 'i' to separate stream step" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void test2(List<String> list) {
    list.stream().mapToInt(String::length).forEach(i -> System.out.println(i));
  }
}