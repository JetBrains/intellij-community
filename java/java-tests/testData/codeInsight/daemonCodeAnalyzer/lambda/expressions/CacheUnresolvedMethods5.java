import java.util.Collections;
import java.util.Map;
import java.util.Optional;

class StreamMainSimplified {
  public static void main(Optional<Map.Entry<Integer, String>> first) {
    String s1 = first.map((e) -> e.getV<caret>alue()).orElse("").substring(9);
  }

}