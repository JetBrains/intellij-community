public class IsNotNullCheck {
   void bar() {
     final Value v = call();
     if (Value.isNotNull(v)) {
       if(<warning descr="Condition 'v == null' is always 'false'">v == null</warning>) {}
     }
   }
   void bar2() {
     final Value v = call();
     System.out.println(v.hashCode());
     if (Value.isNotNull(v)) {

     }
   }
    Value call() {return new Value();}
}
