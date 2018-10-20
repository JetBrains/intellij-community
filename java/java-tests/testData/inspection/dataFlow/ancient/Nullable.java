import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Npe {
   Object foo(@NotNull Object o) {
     return o;
   }

   @Nullable Object nullable() {
     return null;
   }

   void bar() {
     Object o = foo(<warning descr="Argument 'nullable()' might be null">nullable()</warning>); // null should not be passed here.
   }
}