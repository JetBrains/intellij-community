import java.util.function.Function;
class Test {

  Function<String, Runnable> f = (str) -> () -> foo(<selection>""</selection>);

  void foo(String s) {}
}