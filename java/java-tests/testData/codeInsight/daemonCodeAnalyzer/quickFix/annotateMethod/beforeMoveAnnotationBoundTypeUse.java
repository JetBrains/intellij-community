// "Fix all '@NotNull/@Nullable problems' problems in file" "true"
package typeUse;

import java.lang.annotation.*;
import java.util.List;

@Target(ElementType.TYPE_USE) @interface NotNull { }

@Target(ElementType.TYPE_USE) @interface Anno { }
@Target(ElementType.TYPE_USE) @interface Anno2 { }

class X {
  void test1(List<@<caret>NotNull ?> a) {}
  void test2(List<@NotNull ? extends CharSequence> a) {}
  void test3(List<@NotNull ? extends @NotNull CharSequence> a) {}
  void test4(List<@NotNull @Anno ? extends @Anno2 CharSequence> a) {}
}