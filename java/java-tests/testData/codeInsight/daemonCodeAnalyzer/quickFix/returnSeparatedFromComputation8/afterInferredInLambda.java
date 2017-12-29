// "Move 'return' closer to computation of the value of 'r'" "true"
import java.util.stream.Stream;

class T {
  String[] f(String[] a) {
    return Stream.of(a).map(s -> {
        if (s.startsWith("#")) return s.substring(1);
      else if (s.startsWith("//")) return s.substring(2);
      else return s;
    }).toArray(String[]::new);
  }
}