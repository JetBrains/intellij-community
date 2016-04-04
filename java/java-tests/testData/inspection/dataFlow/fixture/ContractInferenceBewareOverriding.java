import org.jetbrains.annotations.Nullable;

class Doo {

  boolean isMaybeNotNull(@Nullable Object o) {
    return o != null;
  }

  void foo(@Nullable String s) {
    if (isMaybeNotNull(s)) {
      System.out.println(s.<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>());
    }
  }

}

class DooImpl extends Doo {
  boolean isMaybeNotNull(@Nullable Object o) {
    return hashCode() == 42;
  }
}