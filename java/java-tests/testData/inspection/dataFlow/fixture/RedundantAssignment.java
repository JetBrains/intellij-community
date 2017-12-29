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
}