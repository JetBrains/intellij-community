// "Extract variable 'l' to 'map' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  long[] testAsLongStream(int[] x) {
    return Arrays.stream(x).map(i -> i * 2).mapToLong(l -> l).toArray();
  }
}