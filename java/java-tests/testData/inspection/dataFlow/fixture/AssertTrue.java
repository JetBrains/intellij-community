public class AssertTrue {
   void bar() {
     final boolean b = call();
     if (Assertions.assertTrue(b)) {
       if(<warning descr="Condition 'b' is always 'true'">b</warning>) {}
     }
   }
   boolean call() {return true;}
}
