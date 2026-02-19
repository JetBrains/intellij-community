// "Replace with 'StringBuilder.repeat()'" "true"
import java.util.Objects;

class Test {
  String hundredTimes(String s) {
    Objects.requireNonNull(s);
    StringBuilder sb = new StringBuilder();
      sb.repeat(s, 100);
    return sb.toString();
  }
}