import static javax.annotation.meta.When.MAYBE;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;

class Main {
  int go(Lib lib) {
    return lib.usingNonnullMaybe().<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>() + lib.usingCheckForNull().<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>();
  }

  interface Lib {
    @UsingNonnullMaybe
    Object usingNonnullMaybe();

    @UsingCheckForNull
    Object usingCheckForNull();
  }

  @Nonnull(when = MAYBE)
  @TypeQualifierNickname
  @interface UsingNonnullMaybe {}

  @CheckForNull
  @TypeQualifierNickname
  @interface UsingCheckForNull {}
}