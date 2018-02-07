import java.util.*;

public class NullabilityJdk9 {

  void test() {
    List<Integer> list = List.of(1,2,3,<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Set<String> set = Set.of("foo", "bar", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, "baz");
    Map<String, Integer> map = Map.of("x", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>,
      "y", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>,
      <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, 3);
    Map<String, Integer> map1 = Map.ofEntries(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Integer[] array = null;
    List<Integer> list1 = List.of(<warning descr="Argument 'array' might be null">array</warning>);
  }
}
