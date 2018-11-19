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
     Object o = foo(<warning descr="Argument '(Object)nullable()' might be null">(Object)nullable()</warning>); // null should not be passed here.
   }
}