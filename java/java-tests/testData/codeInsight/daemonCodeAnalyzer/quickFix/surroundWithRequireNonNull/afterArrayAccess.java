// "Replace with 'Objects.requireNonNull(arr)'" "true"

import java.util.Objects;

class MyClass {
  void test() {
    int[] arr = Math.random() > 0.5 ? null : new int[10];
    System.out.println(Objects.requireNonNull(arr)[1]);
  }
}