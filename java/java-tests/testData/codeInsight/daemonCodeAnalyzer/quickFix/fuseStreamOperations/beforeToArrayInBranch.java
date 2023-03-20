// "Fuse 'toArray' into the Stream API chain" "false"
import java.util.*;
import java.util.stream.*;

class Test {
  public Object[] getArray(String[] input, boolean f) {
    List<String> list = Arrays.stream(input)
      .filter(Objects::nonNull)
      .<caret>collect(Collectors.toList());
    String[] data = new String[] {};
    if (f) {
      return list.toArray();
    }
    list.add("hello");
    return list.toArray();
  }
}