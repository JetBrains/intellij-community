import java.lang.annotation.*;

class Test {
  void test(@Test(1) String @Test(2) [] @Test(3) [] test) {

  }

  @Target(ElementType.TYPE_USE)
  @Documented
  @interface Test {
    int value();
  }
}