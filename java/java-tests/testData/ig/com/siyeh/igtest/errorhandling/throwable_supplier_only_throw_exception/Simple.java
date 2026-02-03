import java.util.Optional;

public class Simple {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");

    optional.<warning descr="Throwable supplier doesn't return any exception">orElseThrow<caret></warning>(() -> {
      throw new RuntimeException();
    });
  }
}