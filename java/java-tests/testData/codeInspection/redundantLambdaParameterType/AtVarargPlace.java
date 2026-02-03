// "Remove redundant types" "false"
import java.util.function.Function;

class Test {
  <K> void f(Function<K, String>... l){}

  {
    f(null, (Str<caret>ing s) -> s);
  }
}