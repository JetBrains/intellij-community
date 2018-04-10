// "Replace with null-checking lambda" "true"

import java.util.Arrays;

class A{
  static String convert(String s) {
    return s.isEmpty() ? s : null;
  }

  void test(String[] data){
    Arrays.stream(data).map(A::convert).map(String::tr<caret>im).forEach(System.out::println);
  }
}