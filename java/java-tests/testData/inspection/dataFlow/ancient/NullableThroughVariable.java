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
     Object o = nullable();
     foo(<warning descr="Argument 'o' might be null">o</warning>); // null should not be passed here
   }
}