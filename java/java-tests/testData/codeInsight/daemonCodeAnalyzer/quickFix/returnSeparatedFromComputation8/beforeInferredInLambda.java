// "Move 'return' closer to computation of the value of 'r'" "true"
import java.util.stream.Stream;

class T {
  String[] f(String[] a) {
    return Stream.of(a).map(s -> {
      String r;
      if (s.startsWith("#")) r = s.substring(1);
      else if (s.startsWith("//")) r = s.substring(2);
      else r = s;
      re<caret>turn r;
    }).toArray(String[]::new);
  }
}