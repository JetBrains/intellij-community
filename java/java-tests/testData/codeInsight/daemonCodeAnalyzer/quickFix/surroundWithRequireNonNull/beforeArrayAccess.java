// "Replace with 'Objects.requireNonNull(arr)'" "true-preview"

class MyClass {
  void test() {
    int[] arr = Math.random() > 0.5 ? null : new int[10];
    System.out.println(arr<caret>[1]);
  }
}