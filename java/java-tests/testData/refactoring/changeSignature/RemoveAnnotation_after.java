import java.lang.annotation.*;

class X {
  void test(int x) {}
  
  @Target(ElementType.TYPE_USE)
  @interface Foo
}