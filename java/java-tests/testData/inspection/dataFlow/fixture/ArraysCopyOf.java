import java.util.Arrays;

public class ArraysCopyOf {
  void test(int[] arr, int size) {
    int[] copy = Arrays.copyOf(arr, 2);
    if (<warning descr="Condition 'copy.length == 2' is always 'true'">copy.length == 2</warning>) {}
    int[] copy2 = Arrays.copyOf(arr, size);
    if (<warning descr="Condition 'size < 0' is always 'false'">size < 0</warning>) {}
    int[] copy3 = Arrays.copyOf(arr, -size);
    if (<warning descr="Condition 'copy3.length == 0' is always 'true'">copy3.length == 0</warning>) {}
    int[] copy4 = Arrays.<warning descr="The call to 'copyOf' always fails as index is out of bounds">copyOf</warning>(arr, copy3.length - 1);
  }
}