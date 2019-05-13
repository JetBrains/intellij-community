import java.util.stream.Stream;

class Test {
  public void filterByBoolean(Stream<Object[]> stream) {
    stream.filter(it -> (Boolean) it[0]);
  }
}