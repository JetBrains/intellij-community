import org.jetbrains.annotations.NotNull;

class Npe {
   Object foo(@NotNull Object o) {
     return o;
   }

   void bar() {
     Object o = foo(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>); // null should not be passed here.
   }
}