
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

class MyTest
{
  private final static Logger LOGGER = Logger.getLogger(MyTest.class.getName());

  public static void test(List<List<String>> testList) {
    testList.forEach( MyTest.bind(MyTest.cast(LOGGER::info), iterable -> ""));
  }

  private static <A1, A2> TestConsumer<A1, A2> bind(Consumer<? super A2> delegate, Function<A1, A2> function) {
    return null;
  }

  private static <C1> Consumer<C1> cast(Consumer<C1> consumer)
  {
    return consumer;
  }

  private interface TestConsumer<T1, T2> extends Consumer<T1> { }

}