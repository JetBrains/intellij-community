import java.lang.annotation.*;
import java.util.List;

@Foo(1)
class Test extends @Foo(2) List<@Foo(3) String> {
}

@Documented
@Target(ElementType.TYPE_USE)
@interface Foo {
  int value();
}
