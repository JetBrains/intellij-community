import java.util.List;
import java.util.stream.Stream;

class Main {
  public void dropWhileSimple() {
    if (<warning descr="Condition 'Stream.of(\"a\", \"bb\", \"c\") .dropWhile(s -> s.length() > 1) .anyMatch(s -> s.equals(\"c\"))' is always 'true'">Stream.of("a", "bb", "c")
      .dropWhile(s -> <warning descr="Condition 's.length() > 1' is always 'false'">s.length() > 1</warning>)
      .anyMatch(s -> s.equals("c"))</warning>) {}
  }

  public void takeWhileSimple() {
    long cnt = <warning descr="Result of 'Stream.of(\"a\", \"bb\", \"c\") .takeWhile(s -> s.length() > 1) .count()' is always '0'">Stream.of("a", "bb", "c")
      .takeWhile(s -> <warning descr="Condition 's.length() > 1' is always 'false'">s.length() > 1</warning>)
      .count()</warning>;
    if (<warning descr="Condition 'cnt == 1' is always 'false'">cnt == 1</warning>) {}

    if (<warning descr="Condition 'Stream.of(\"a\", \"bb\", \"c\") .takeWhile(s -> s.length() == 1) .anyMatch(s -> s.equals(\"c\"))' is always 'false'">Stream.of("a", "bb", "c")
      .takeWhile(s -> s.length() == 1)
      .anyMatch(s -> <warning descr="Result of 's.equals(\"c\")' is always 'false'">s.equals("c")</warning>)</warning>) {}
  }
  
  public static List<String> no0(Stream<String> strings) {
    final boolean[] found0 = {false};
    List<String> list = strings.takeWhile(s -> {
      found0[0] = s.equals("0");
      return !found0[0];
    }).toList();

    return found0[0] ? List.of() : list;
  }
}