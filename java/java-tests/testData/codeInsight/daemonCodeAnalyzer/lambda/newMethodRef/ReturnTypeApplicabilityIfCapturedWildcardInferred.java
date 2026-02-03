import java.util.Map.Entry;
import java.util.function.Function;

class Test {

  {
    Function<Entry<? extends String, Integer>, String> getKey = Entry::getKey;
  }
}