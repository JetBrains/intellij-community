import org.jetbrains.annotations.NotNull;

public class Npe {
   Object foo(@NotNull Object o) {
     if (o == null) {
       // Should not get there.
     }
     return o;
   }
}