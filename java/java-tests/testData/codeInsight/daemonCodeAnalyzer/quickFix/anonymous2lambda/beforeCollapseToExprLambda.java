// "Replace with lambda" "true-preview"
import java.util.Collection;
import java.util.function.Function;

class Test {

  public static <T, V> V[] map2Array( T[] array, Class<? super V> aClass, Function<T, V> mapper) {
    return null;
  }
  public static <T, V> V[] map2Array(Collection<T> array, Class<? super V> aClass, Function<T, V> mapper) {
    return null;
  }

  void m(String[] f, int i, FooBar manager){

    map2Array(f, Integer.class, new Nullable<caret>Function<String, Integer>() {
      @Override
      public Integer apply(String s) {
        return s.length();
      }
    });
  }

  interface NullableFunction<A, B> extends Function<A, B> {

    B apply(final A param);
  }
}