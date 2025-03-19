import org.jetbrains.annotations.NotNull;

import java.util.*;

public class NullabilityJdk11 {

  void test(List<String> input) {
    List<String> listArray = List.of(new String[]{"x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", <warning descr="'null' is stored to an array of @NotNull elements">null</warning>});
    if (<warning descr="Condition 'List.copyOf(input).get(12) == null' is always 'false'">List.copyOf(input).get(12) == null</warning>) {}
    
    List<Integer> list = List.of(1,2,3,<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Set<String> set = Set.of("foo", "bar", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, "baz");
    Map<String, Integer> map = Map.of("x", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>,
      "y", <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>,
      <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, 3);
    Map<String, Integer> map1 = Map.ofEntries(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Integer[] array = null;
    List<Integer> list1 = List.of(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">array</warning>);
  }

  void cmp(Comparator<@NotNull String> cmp) {
    if (cmp.compare(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, "hello") == 0) return;
    if (Comparator.nullsFirst(cmp).compare(null, "hello") == 0) return;
  }
}
