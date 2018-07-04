import org.jetbrains.annotations.Nullable;
import java.util.*;

class DDD {
  @Nullable
  String field;

  @Nullable List<String> list;

  void test2() {
    if(list == null) list = new ArrayList<>();
    unknown();
    System.out.println(list.size());
  }

  void unknown() {
    System.out.println();
  }

  int test() {
    return new DDD().field.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
  }
}
