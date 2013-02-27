public class IsNullCheck {
   void bar() {
     final Value v = call();
     if (Value.isNull(v)) {
       if(<warning descr="Condition 'v == null' is always 'true'">v == null</warning>) {}
     }
   }
    Value call() {return new Value();}
}
