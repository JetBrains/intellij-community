import java.lang.annotation.*;

class X {
  void <caret>test(@Foo int x) {}
  
  @Target(ElementType.TYPE_USE)
  @interface Foo
}