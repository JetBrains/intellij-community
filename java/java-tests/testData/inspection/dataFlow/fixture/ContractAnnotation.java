import org.jetbrains.annotations.Contract;

import java.lang.*;
import java.lang.IllegalArgumentException;

public class AssertIsNotNull {
  void bar() {
    final Object o = call();
    assertIsNotNull(o);
    if(<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
  }
  
  @Contract("null -> fail")
  static void assertIsNotNull(Object o) {
    if (o == null) {
      throw new IllegalArgumentException();
    }
  }
  
  Object call() {return new Object();}
}
