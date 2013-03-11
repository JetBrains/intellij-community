public class AssertFalse {
   void bar() {
     final boolean b = call();
     if (Assertions.assertFalse(b)) {
       if(<warning descr="Condition 'b' is always 'false'">b</warning>) {}
     }
   }
   boolean call() {return true;}
}
