// "Adapt using 'Arrays.stream()'" "true-preview"
import java.util.*;
import java.util.stream.Stream;

public class Test {
  Stream<String> testStream(List<String[]> list) {
    return list.<caret>get(0);
  }
}
