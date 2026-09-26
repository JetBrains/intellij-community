// "Replace lambda with method reference" "false"
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

class Test {
  public Test(String s) {}

  public static void define(Supplier<?> moduleConstructor){}
  public static void define(Function<?, ?> moduleConstructor){}
  public static void define(BiFunction<?, ?, ?> moduleConstructor){}

  {
    define((String s) -> new Test<caret>(s));
  }
}
