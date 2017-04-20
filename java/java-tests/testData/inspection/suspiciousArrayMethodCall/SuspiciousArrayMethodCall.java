import java.util.Arrays;

public class SuspiciousArrayMethodCall {
  void test() {
    int[][] data = new int[10][];
    Arrays.fill(data, <warning descr="Element type is not compatible with array type">-1</warning>);
  }

  void testBoxing() {
    int[] data = new int[10];
    Arrays.fill(data, -1);
  }

  int findString(String[] data) {
    return Arrays.binarySearch(data, <warning descr="Element type is not compatible with array type">1</warning>);
  }

  int findCharSequence(CharSequence[] data, Integer i) {
    return Arrays.binarySearch(data, <warning descr="Element type is not compatible with array type">i</warning>);
  }

  int findCharSequenceNumber(CharSequence[] data, Number n) {
    return Arrays.binarySearch(data, n);
  }

  boolean testEquality(String[] arr, Integer[] arr2) {
    return Arrays.<warning descr="Array types are incompatible: arrays are always different">equals</warning>(arr, arr2) ||
           Arrays.<warning descr="Array types are incompatible: arrays are always different">equals</warning>(arr2, arr);
  }
}