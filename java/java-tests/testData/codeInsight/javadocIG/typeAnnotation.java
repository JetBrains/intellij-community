import java.lang.annotation.*;
import java.util.List;

class Test {
  public <@Foo(1) T extends @Foo(2) CharSequence & @Foo(3) Cloneable>
  @Foo(4) List<@Foo(5) ? super @Foo(6) T> <caret>bar() {return null;}

  @Documented
  @Target(ElementType.TYPE_USE)
  @interface Foo {
    int value();
  }
}
