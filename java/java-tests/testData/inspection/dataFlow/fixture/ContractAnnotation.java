import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.*;
import java.lang.AssertionError;
import java.lang.IllegalArgumentException;

class AssertIsNotNull {
  void bar(String s, String s1) {
    if (<warning descr="Condition 's == null && trimIfNotNull(s) != null' is always 'false'">s == null && <warning descr="Condition 'trimIfNotNull(s) != null' is always 'false' when reached">trimIfNotNull(s) != null</warning></warning>) {
      throw new AssertionError();
    }

    final Object o = call();
    assertIsNotNull(o);
    System.out.println(o.toString());
    if(<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
    
    if (trimIfNotNull(s1) != null) {
      System.out.println(s1.charAt(0));
      if (<warning descr="Condition 's1 == null' is always 'false'">s1 == null</warning>) {
        
      }
    }
  }
  
  @Contract("null -> fail")
  static void assertIsNotNull(Object o) {
    if (o == null) {
      throw new IllegalArgumentException();
    }
  }

  @Contract("null -> null; !null -> !null")
  @Nullable static String trimIfNotNull(@Nullable String s) {
    if (s == null) {
      return null;
    }
    return s.trim();
  }
  
  Object call() {return new Object();}
  
}
