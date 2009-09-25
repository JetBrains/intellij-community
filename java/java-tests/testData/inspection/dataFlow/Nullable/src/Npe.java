import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Npe {
   Object foo(@NotNull Object o) {
     return o;
   }

   @Nullable Object nullable() {
     return null;
   }

   void bar() {
     Object o = foo(nullable()); // null should not be passed here.
   }
}