// "Unroll loop" "true"
import java.util.Arrays;

class X {
  enum MyEnum {
    A, B, C;
  }
  
  void testLoop() {
      System.out.println(MyEnum.A);
      System.out.println(MyEnum.B);
      System.out.println(MyEnum.C);
  }
}