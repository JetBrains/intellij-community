import java.util.Arrays;

public class ArraysCopyOf {
  public static int[] indexesOf(Object[] array, Object element) {
    int[] tmp = new int[0];

    int i = 0;
    int o = 0;
    while (i < array.length) {
      i = indexOf(array, element, i);
      if (i == -1) {
        break;
      }

      tmp = Arrays.copyOf(tmp, tmp.length + 1);
      tmp[o++] = i;
      i++;
    }

    return  tmp;
  }
  
  native static int indexOf(Object[] arr, Object e, int i);
  
  void test(int[] arr, int size) {
    int[] copy = Arrays.copyOf(arr, 2);
    if (<warning descr="Condition 'copy.length == 2' is always 'true'">copy.length == 2</warning>) {}
    int[] copy2 = Arrays.copyOf(arr, size);
    if (<warning descr="Condition 'size < 0' is always 'false'">size < 0</warning>) {}
    int[] copy3 = Arrays.copyOf(arr, -size);
    if (<warning descr="Condition 'copy3.length == 0' is always 'true'">copy3.length == 0</warning>) {}
    int[] copy4 = Arrays.<warning descr="The call to 'copyOf' always fails as index is out of bounds">copyOf</warning>(arr, copy3.length - 1);
  }
  
  void test2(int[] arr, int size) {
    int[] copy = Arrays.copyOf(arr, size);
    if (<warning descr="Condition 'copy.length == size' is always 'true'">copy.length == size</warning>) {}
  }
}