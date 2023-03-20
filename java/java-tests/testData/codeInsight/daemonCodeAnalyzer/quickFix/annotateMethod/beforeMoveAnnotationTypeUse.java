// "Fix all '@NotNull/@Nullable problems' problems in file" "true"
package typeUse;

import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) public @interface NotNull { }
@Target(ElementType.TYPE_USE) public @interface Nullable { }

class X {
  @<caret>NotNull byte[] getBytes(@Nullable byte[] input, @NotNull byte another) {return null;}
}