// "Replace with toArray" "true"
import java.util.*;

public class Test {
  Object[] test(List<String[]> list) {
      return list.stream().filter(Objects::nonNull).flatMap(Arrays::stream).sorted().toArray();
  }
}
