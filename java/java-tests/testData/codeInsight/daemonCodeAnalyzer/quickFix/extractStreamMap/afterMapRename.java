// "Extract variable 'l' to 'asLongStream' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  long[] testMapRename(int[] x) {
    return Arrays.stream(x).asLongStream().map(l -> l * l).toArray();
  }
}