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
        <warning descr="Value assigned to the variable is already assigned to it">flag</warning> = false;
      }
    }
    System.out.println(flag);
  }

  void fillArray(int[] arr) {
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    <warning descr="Value assigned to the variable is already assigned to it">arr[0]</warning> = 1;
  }

  void withTest(int x) {
    if(x != 0) {
      System.out.println(x);
    } else {
      <warning descr="Value assigned to the variable is already assigned to it">x</warning> = 0;
    }
    System.out.println("oops");
  }

  void var(Object b) {
    Object a = b;
    if(b.hashCode() > 10) {
      a = null;
    } else {
      <warning descr="Value assigned to the variable is already assigned to it">a</warning> = b;
    }
  }
}