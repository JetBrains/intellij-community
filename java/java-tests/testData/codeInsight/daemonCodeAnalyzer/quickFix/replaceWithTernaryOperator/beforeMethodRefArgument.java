// "Replace with null-checking lambda" "true-preview"

import java.util.Arrays;

class A{
  static String convert(String s) {
    return s.isEmpty() ? s : null;
  }

  void test(String[] data){
    Arrays.stream(data).map(A::convert).map(A::con<caret>vert).forEach(System.out::println);
  }
}