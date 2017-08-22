// "Replace with ' != null ?:'" "true"

import java.util.Arrays;

class A{
  static String convert(String s) {
    return s.isEmpty() ? s : null;
  }

  void test(String[] data){
    Arrays.stream(data).map(A::convert).map(s -> s != null ? convert(s) : null).forEach(System.out::println);
  }
}