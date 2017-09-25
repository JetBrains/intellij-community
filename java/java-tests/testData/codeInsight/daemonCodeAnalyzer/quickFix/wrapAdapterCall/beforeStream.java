// "Wrap using 'Arrays.stream()'" "true"
import java.util.*;
import java.util.stream.Stream;

public class Test {
  Stream<String> testStream(List<String[]> list) {
    return list.<caret>get(0);
  }
}
