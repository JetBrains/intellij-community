import java.io.IOException;

class Test {
  @FunctionalInterface
  public interface ConsumerThatThrows<T, E extends Throwable> {
    void accept(T var1) throws E;
  }

  public static void main(String[] args) throws IOException
  {
    acceptsConsumerThatThrows(Test::methodThatThrows, "hello");
  }

  public static <T, E extends Exception> void acceptsConsumerThatThrows(ConsumerThatThrows<T, E> consumer, T t) throws E
  {
    consumer.accept(t);
  }

  public static void methodThatThrows(String s) throws IOException {}
}