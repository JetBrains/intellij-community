import java.util.stream.Stream;

class Test {
  public static void main(String[] args) {
    long thread = Thread.currentThread().getId();

    Stream.of(1, 2, 3, 4)
      .parallel()
      .forEach(e -> {
        if (Thread.currentThread().getId() == thread) {

        }
      });

  }
}
