import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Main<T> {
}

class Use extends Main<Double> {
  void test(Main<String> main) {

  }

  void testNullable(Main<<warning descr="Non-null type argument is expected">@Nullable Integer</warning>> nullable) {

  }

}