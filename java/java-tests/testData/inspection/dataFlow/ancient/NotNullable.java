import org.jetbrains.annotations.NotNull;

class Npe {
   @NotNull Object foo() {
     return new Object();
   }

   void bar() {
     Object o = foo();
     if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) System.out.println("Can't be");
   }
}