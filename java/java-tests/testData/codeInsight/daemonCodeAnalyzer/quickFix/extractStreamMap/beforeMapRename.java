// "Extract variable 'l' to separate stream step" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  long[] testMapRename(int[] x) {
    return Arrays.stream(x).mapToLong(i -> {
      long <caret>l = i;
      return l * l;
    }).toArray();
  }
}