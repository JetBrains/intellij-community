// "Unroll loop" "true"
import java.util.Arrays;

class X {
  enum MyEnum {
    A, B, C;
  }
  
  void testLoop() {
    <caret>for(MyEnum x : MyEnum.values()) {
      System.out.println(x);
    }
  }
}