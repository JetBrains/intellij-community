import java.util.List;
import java.util.function.Function;

class Test {

  {
    transform(1,  (String l) -> null);
  }

  public static <I, O> void transform(int input, Function<I,  O> function) {
    System.out.println(input);
    System.out.println(function);
  }

  interface IFunction<F, T> {
    List<T> apply(F var1);
  }
  public static <I, O> void transform(int input, IFunction<I, O> function) {
    System.out.println(input);
    System.out.println(function);
  }
}
