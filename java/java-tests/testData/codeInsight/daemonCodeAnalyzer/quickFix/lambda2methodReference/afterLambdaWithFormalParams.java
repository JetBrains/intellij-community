// "Replace lambda with method reference" "true"
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

class Test {
  public Test(String s) {}

  public static void define(Supplier<?> moduleConstructor){}
  public static void define(Function<?, ?> moduleConstructor){}
  public static void define(BiFunction<?, ?, ?> moduleConstructor){}

  {
    define((Function<String, Object>) Test::new);
  }
}
