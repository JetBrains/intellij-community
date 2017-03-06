// "Extract variable 'l' to separate mapping method" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  Object[] testBoxed(int[] x) {
    return Arrays.stream(x).asLongStream().mapToObj(l -> l).toArray();
  }
}