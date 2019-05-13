
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

class Foo {
  public static void foo() {

    Function<Entry<Long, Set<Integer>>, Long> getKey = Entry::getKey;

  }
}