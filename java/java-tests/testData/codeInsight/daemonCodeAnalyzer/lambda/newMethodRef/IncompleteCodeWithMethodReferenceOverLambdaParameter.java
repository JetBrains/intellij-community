
import java.util.function.Function;
class Test {
  {
    foo(s -> {
      foo(s::concat);
      return s;
    });
  }

  <S,<error descr="'>' expected."><error descr="Type parameter expected"> </error></error>  void foo(Function<String, String> f){}
}
