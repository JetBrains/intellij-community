import java.util.function.Function;
class Test {

  Function<String, Runnable> f = (str) -> () -> {
      String s = "";
      foo(s);
  };

  void foo(String s) {}
}