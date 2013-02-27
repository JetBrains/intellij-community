import java.lang.*;

public class AssertIsNotNull {
  void bar() {
    final Object o = call();
    Assertions.assertIsNotNull(o);
    if(<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
  }
  Object call() {return new Object();}
}
