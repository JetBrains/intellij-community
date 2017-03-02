// "Extract variable 'arr' to separate stream step" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  String testArrayInitializer() {
    return LongStream.of(1, 2, 3).mapToObj(x -> new long[]{x}).map(arr -> Arrays.toString(arr)).collect(Collectors.joining(","));
  }
}