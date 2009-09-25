import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Npe {
   void bar() {
     final @Nullable Object o = foo();
     o.hashCode(); // NPE
   }

   Object foo() {
     return null;
   }
}