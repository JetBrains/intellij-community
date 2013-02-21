import java.lang.*;

public class AssertIsNull {
   void bar() {
     final Object o = call();
     Assertions.assertIsNull(o);
     if(<warning descr="Condition 'o == null' is always 'true'">o == null</warning>) {}
   }
   Object call() {return new Object();}
}
