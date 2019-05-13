import java.util.stream.Stream;

class InChain {
  public static void main(String[] args) {
    Stream.of("a").map((String<caret> s) -> s + "1").forEach(System.out::println);
  }
}