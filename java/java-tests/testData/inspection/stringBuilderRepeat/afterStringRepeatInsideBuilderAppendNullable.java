import java.util.Objects;

// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces(String s, int i) {
    StringBuilder sb = new StringBuilder();
    sb.repeat(Objects.requireNonNull(s), i);
    return sb.toString();
  }
}