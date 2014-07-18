import org.jetbrains.annotations.Nullable;

class Doo {

  boolean isMaybeNotNull(@Nullable Object o) {
    return o != null;
  }

  void foo(@Nullable String s) {
    if (isMaybeNotNull(s)) {
      System.out.println(<warning descr="Method invocation 's.length()' may produce 'java.lang.NullPointerException'">s.length()</warning>);
    }
  }

}

class DooImpl extends Doo {
  boolean isMaybeNotNull(@Nullable Object o) {
    return hashCode() == 42;
  }
}