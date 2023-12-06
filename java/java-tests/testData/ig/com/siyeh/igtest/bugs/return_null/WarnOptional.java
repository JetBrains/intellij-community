import java.util.Optional;
import java.util.OptionalInt;
import java.util.*;
import java.util.function.*;

class WarnOptional {

  private Optional<String> go() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  public OptionalInt nothing() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  Object give() {
    return null;
  }

  private int[] giveMore() {
    return null;
  }
}
interface A<T> {
  T m();
}
class B implements A<Void> {
  public Void m() {
    return  null;
  }

  void bar() {
    Map<String, String> map = new HashMap<>();
    map.compute("foo", (k, v) -> {
      return Math.random() < 0.5 ? v : null; // <- false-positive warning 'return of null'
    });
    map.compute("foo", (k, v) -> Math.random() < 0.5 ? v : null);
    final BiFunction<String, String, String> x = (k, v) -> Math.random() < 0.5 ? k : null;
    final BiFunction<String, String, String> y = (k, v) -> {
      return Math.random() < 0.5 ? k : null;
    };
    final BiFunction<String, String, String> z = new BiFunction<String, String, String>() {
      @Override
      public String apply(String k, String v) {
        return Math.random() < 0.5 ? k : null;
      }
    };
  }
}