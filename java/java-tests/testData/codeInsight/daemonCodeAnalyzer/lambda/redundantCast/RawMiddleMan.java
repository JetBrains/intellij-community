
import java.util.function.Function;

abstract class MyTest {
  public <T> T m() {
    //noinspection unchecked
    return (T)(Function)t -> t;
  }
  
  public Function m1() {
    //noinspection unchecked
    return (<warning descr="Casting '(Function)t -> {...}' to 'Function' is redundant">Function</warning>)(<warning descr="Casting 't -> {...}' to 'Function' is redundant">Function</warning>)t -> t;
  }
}
