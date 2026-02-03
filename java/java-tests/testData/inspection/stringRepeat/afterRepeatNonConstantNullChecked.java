// "Replace with 'String.repeat()'" "true"
import java.util.Objects;

class Test {
  String hundredTimes(String s) {
    Objects.requireNonNull(s);
    StringBuilder sb = new StringBuilder();
      sb.append(s.repeat(100));
    return sb.toString();
  }
}