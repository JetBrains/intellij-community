public class IsNullCheck {
   void bar() {
     final Value v = call();
     if (Value.isNull(v)) {
       return;
     }
     if(<warning descr="Condition 'v == null' is always 'false'">v == null</warning>) {}
   }
    Value call() {return new Value();}
}
