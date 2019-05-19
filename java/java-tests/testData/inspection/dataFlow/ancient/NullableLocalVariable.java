import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Npe {
   void bar() {
     final @Nullable Object o = foo();
     o.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>(); // NPE
   }

   @Nullable Object foo() {
     return null;
   }
}
