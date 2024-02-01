import java.util.Optional;

public class Test {
  public static void main(String[] args) {
    testString(Optional.of("World "));
    testString(Optional.empty());
  }

  static void testString(Optional<String> s) {
    if (s.isEmpty()) {
      System.out.println("oops");
    }
    if (s.isPresent()) {
      s = s.map(string -> string + "hello");
      System.out.println(s.orElse(null));
    }
    s = Optional.of(s.orElse(null) + null);
    System.out.println(s.get().trim());
    s = Optional.empty();
  }
}