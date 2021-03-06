// "Unroll loop" "true"
import java.util.Arrays;

class X {
  void test() {
    int[] array = new int[10];
    Arrays.setAll(array, i -> i);
      System.out.println(array[0]);
      System.out.println(array[1]);
      System.out.println(array[2]);
      System.out.println(array[3]);
      System.out.println(array[4]);
      System.out.println(array[5]);
      System.out.println(array[6]);
      System.out.println(array[7]);
      System.out.println(array[8]);
      System.out.println(array[9]);
  }
}