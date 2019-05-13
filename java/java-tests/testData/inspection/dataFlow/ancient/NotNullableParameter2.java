import org.jetbrains.annotations.NotNull;

class Npe {
   Object foo(@NotNull Object o) {
     if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {
       // Should not get there.
     }
     return o;
   }
}