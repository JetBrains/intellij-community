import java.util.Arrays;

class Test {
  void test(int[] dest) {
    int[] array = {1, 2, 3, 4, 5};
    System.out.println(Arrays.toString(array));
    System.arraycopy(array, 0, dest, 0, 5);
    // Analysis cannot see this, unfortunately
    if (array[3] == 4) {
    }
  }

  void test2(int[] dest) {
    int[] array = {1, 2, 3, 4, 5};
    System.out.println(Arrays.toString(array));
    System.arraycopy(dest, 0, array, 0, 5);
    if (array[3] == 4) {
    }
  }
}