// "Collapse loop with stream 'forEach()'" "true-preview"

public class Test {
  void collectNames() {
    int[] arr1 = {1, 2, 3};
    int[] arr2 = {1, 2, 3};
    deepEquals(arr1, arr2);
    for <caret>(int i = 0; i < arr1.length; i++) {
      int a = arr1[i];
      int b = arr2[i];
      boolean result = java.util.Objects.deepEquals(a, b);
      System.out.println("result is " + result);
    }
  }
}
