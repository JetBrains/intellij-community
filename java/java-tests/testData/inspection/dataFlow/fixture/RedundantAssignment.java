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
        <warning descr="Variable is already assigned to this value">flag</warning> = false;
      }
    }
    System.out.println(flag);
  }

  void fillArray(int[] arr) {
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    <warning descr="Variable is already assigned to this value">arr[0]</warning> = 1;
    <warning descr="Variable update does nothing">arr[4]</warning> += 0;
  }

  void withTest(int x) {
    if(x != 0) {
      System.out.println(x);
    } else {
      <warning descr="Variable is already assigned to this value">x</warning> = 0;
    }
    System.out.println("oops");
  }

  void var(Object b) {
    Object a = b;
    if(b.hashCode() > 10) {
      a = null;
    } else {
      <warning descr="Variable is already assigned to this value">a</warning> = b;
    }
  }
  
  void testOneMinusOne(int a) {
    int b = 0;
    if(a < 1 && a > -1) {
      <warning descr="Variable is already assigned to this value">b</warning> = a;
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
      <warning descr="Variable is already assigned to this value">type<caret>Path</warning> = typePath.append(Integer.valueOf(index));
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
