import java.util.Comparator;

public class Test<T extends Test<T>> {
  Comparator<Test<?>> bySize = Comparator.comparingInt(Test::size);

  public int size() {
    return 0;
  }
}
