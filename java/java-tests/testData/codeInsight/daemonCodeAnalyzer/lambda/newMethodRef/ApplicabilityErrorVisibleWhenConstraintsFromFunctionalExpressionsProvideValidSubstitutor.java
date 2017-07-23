
import java.util.function.Function;

class Test {

  public static void main(Builder<Beta> gammaBuilder) {
    <error descr="Inferred type 'B' for type parameter 'B' is not within its bound; should extend 'Test.Builder<Test.Alfa>'">a(Test::c, name -> gammaBuilder)</error>;
  }


  private static <T extends Alfa, B extends Builder<T>> void a(final Function<B, String> f1,
                                                               final Function<String, B> f2) {

  }

  private static String c(final Builder<? extends Beta> builder) {
    return "";
  }

  static class Alfa {}
  private static class Beta extends Alfa {}

  static class Builder<T> {}
}