import java.util.stream.*;

class Test {
  public static void main(String... args) {
    String res = Stream.of("elvis").reduce(null, (a, b) -> b);

    if (<warning descr="Condition 'res.equals(\"elvis\")' is always 'true'">res.equals("elvis")</warning>) {}
  }
}