import java.util.Optional;

public class Several {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");
    optional.orElseThrow(() -> new RuntimeException());
    optional.<warning descr="Throwable supplier doesn't return any exception">orElseThrow<caret></warning>(() -> {
      if (args.length == 1) {
        throw new RuntimeException();
      } else {
        throw new RuntimeException();
      }
    });
    optional.<warning descr="Throwable supplier doesn't return any exception">orElseThrow</warning>(() -> {
      try {
        throw new RuntimeException();
      }
      catch (Exception e) {
      }
      try {
        throw new ArithmeticException();
      }
      catch (NullPointerException e) {
      }
      throw new RuntimeException();
    });
  }
}