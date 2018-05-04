import java.util.*;
import org.jetbrains.annotations.*;

class ContractReturnValues {
  void testRequireNonNull() {
    System.out.println(Objects.requireNonNull(nullable()).trim());
    System.out.println(nullable().<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
  }

  void test(StringBuilder sb) {
    StringBuilder sb1 = sb.append("foo");
    if (<warning descr="Condition 'sb1 == sb' is always 'true'">sb1 == sb</warning>) {
      System.out.println("Always");
    }
  }

  void test2(Object o) {
    if(<warning descr="Condition 'Objects.requireNonNull(o) != o' is always 'false'">Objects.requireNonNull(o) != o</warning>) {
      System.out.println("???");
    }
  }

  void testLocality(int[] data) {
    int[] clone = Arrays.copyOf(data, 10);
    clone[1] = 5;
    data[1] = 5;
    unknown();
    if(data[1] == 5) {
      System.out.println("who knows? unknown() could modify data");
    }
    if(<warning descr="Condition 'clone[1] == 5' is always 'true'">clone[1] == 5</warning>) {
      System.out.println("always");
    }
  }

  native void unknown();

  @Nullable
  native String nullable();
}
