public class IsNotNullCheck {
   void bar() {
     final Value v = call();
     if (Value.isNotNull(v)) {
       if(<warning descr="Condition 'v == null' is always 'false'">v == null</warning>) {}
     }
   }
    Value call() {return new Value();}
}
