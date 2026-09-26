import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Main<T> {
}

class Use extends Main<<warning descr="Non-null type argument is expected">Double</warning>> {
  void test(Main<<warning descr="Non-null type argument is expected">String</warning>> main) {

  }

  void testNullable(Main<<warning descr="Non-null type argument is expected">@Nullable Integer</warning>> nullable) {

  }

}