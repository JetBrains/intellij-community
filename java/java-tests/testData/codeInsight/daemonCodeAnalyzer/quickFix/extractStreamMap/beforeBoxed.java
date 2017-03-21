// "Extract variable 'l' to 'asLongStream' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  Object[] testBoxed(int[] x) {
    return Arrays.stream(x).mapToObj(i -> {
      long <caret>l = i;
      return l;
    }).toArray();
  }
}