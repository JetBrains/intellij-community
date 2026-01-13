import typeUse.*;
import java.util.*;

class JC {

  void testMap() {
    Map<@NotNull String, @NotNull String> <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">m1</warning> = new HashMap<@Nullable String, String>();
    m1 <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">=</warning> new HashMap<String, @Nullable String>();
    m1 = new HashMap<String, String>();

    Map<@NotNull String, ? extends @NotNull String> <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">m2</warning> = new HashMap<@Nullable String, String>();
  }

}
