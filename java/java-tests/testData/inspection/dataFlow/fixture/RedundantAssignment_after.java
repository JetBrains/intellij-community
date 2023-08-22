import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantAssignment {
  void test(int x) {
    boolean flag = false;
    if(x > 2) {
      if(x % 3 == 1) {
        flag = true;
      } else {
        flag = false;
      }
    }
    System.out.println(flag);
  }

  void fillArray(int[] arr) {
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    arr[0] = 1;
    arr[4] += 0;
  }

  void withTest(int x) {
    if(x != 0) {
      System.out.println(x);
    } else {
      x = 0;
    }
    System.out.println("oops");
  }

  void var(Object b) {
    Object a = b;
    if(b.hashCode() > 10) {
      a = null;
    } else {
      a = b;
    }
  }
  
  void testOneMinusOne(int a) {
    int b = 0;
    if(a < 1 && a > -1) {
      b = a;
    }
    System.out.println(b);
  }
  
  class X {
    int a;
    int b;

    {
      a = 0;
      ((b)) = 0;
    }
  }
}
class A {
  String typePath;
  void f() {
    StringBuilder typePath = new StringBuilder(this.typePath);
    for (int index = 0; index < 10; index++) {
       <caret> typePath.append(Integer.valueOf(index));
    }
  }
}
class TestConstant {
  static final byte NONE = 0;
  byte x;
  
  TestConstant() {
    x = NONE;
  }
}
// IDEA-258765
interface Intersection {
  interface I { }
  final class A { }
  class Data<T extends A & I> {
    final T value;
    private Data(T value) { this.value = value; }
  }
}