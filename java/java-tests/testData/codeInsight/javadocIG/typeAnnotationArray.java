import java.lang.annotation.*;

class Test {
  public @Foo(1) String @Foo(2) @Bar [] <caret>foo(){}
}
@Documented
@Target(ElementType.TYPE_USE)
@interface Foo {
  int value();
}

@Target(ElementType.TYPE_USE)
@interface Bar {} 