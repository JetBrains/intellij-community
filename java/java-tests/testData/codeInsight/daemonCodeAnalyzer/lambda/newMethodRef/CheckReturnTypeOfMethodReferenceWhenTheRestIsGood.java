
import java.util.List;

class Test {

  @FunctionalInterface
  interface SomeInterface<T, R> {
    R someMethod(T val);
  }

  public <T, R> void consume(SomeInterface<T, R> someInterface) {
  }

  private List<Integer> produce(Integer val) throws Exception {
    return null;
  }

  public void failure() {
    consume(<error descr="Unhandled exception: java.lang.Exception">this::produce</error>);
  }
}