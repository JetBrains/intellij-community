// "Extract variable 'arr' to 'mapToObj' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  String testArrayInitializer() {
    return LongStream.of(1, 2, 3).mapToObj(x -> new long[]{x}).map(arr -> Arrays.toString(arr)).collect(Collectors.joining(","));
  }
}