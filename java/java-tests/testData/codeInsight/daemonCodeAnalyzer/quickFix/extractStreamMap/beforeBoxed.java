// "Extract variable 'l' to 'asLongStream' operation" "true-preview"
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