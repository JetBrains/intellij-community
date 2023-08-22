import java.util.Arrays;

import org.jetbrains.annotations.Contract;

// IDEA-260755
class X {
  @Contract(pure = true)
  boolean test(int[] arr1, int[] arr2) {
    return Arrays.equals(arr1, arr2);
  }
}