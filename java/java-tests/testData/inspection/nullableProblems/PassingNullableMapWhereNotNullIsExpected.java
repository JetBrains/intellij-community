import typeUse.*;
import java.util.*;

class JC {

  void testMap() {
    Map<@NotNull String, @NotNull String> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">m1</warning> = new HashMap<@Nullable String, String>();
    m1 <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">=</warning> new HashMap<String, @Nullable String>();
    m1 = new HashMap<String, String>();

    Map<@NotNull String, ? extends @NotNull String> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">m2</warning> = new HashMap<@Nullable String, String>();
  }

}
