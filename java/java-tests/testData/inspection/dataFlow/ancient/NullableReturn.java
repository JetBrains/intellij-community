import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Npe {
   @NotNull Object foo() {
     return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
   }
}