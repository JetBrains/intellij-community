import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

class Foo<T> {


  {
    bar(() -> Result.create(new Function<String, String>() {
          @Override
          public String apply(String s) {
            throw new UnsupportedOperationException();
          }
        }, new Object())
    );
  }

  private static <T> void bar(Supplier<T> provider ){}

  static class Result<K> {
    public static <T> Result<T> create( T value, Collection<?> dependencies) {
      return new Result<T>();
    }

    public static <T> Result<T> create( T value, Object... dependencies) {
      return new Result<T>();
    }

  }
}