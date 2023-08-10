import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

class Demo {
  void test(List<String> list) {
    Map<String, List<String>> map1 = list.stream()
      .filter(Objects::isNull)
      .collect(Collectors.groupingBy(x -> x.<warning descr="Method invocation 'trim' will produce 'NullPointerException'">trim</warning>()));
    if (list.isEmpty() && <warning descr="Condition 'map1.isEmpty()' is always 'true' when reached">map1.isEmpty()</warning>) {}
    Map<String, List<String>> map2 = list.stream()
      .filter(Objects::isNull)
      .collect(Collectors.groupingByConcurrent(x -> x.<warning descr="Method invocation 'trim' will produce 'NullPointerException'">trim</warning>(), Collectors.toList()));
    Map<String, List<String>> map3 = list.stream()
      .filter(Objects::isNull)
      .collect(Collectors.groupingBy(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>));
    Map<String, List<String>> map4 = list.stream()
      .filter(Objects::isNull)
      .collect(Collectors.groupingBy(x -> <warning descr="Function may return null, but it's not allowed here">null</warning>));
    Map<Boolean, List<String>> map5 = list.stream()
      .filter(x -> !x.isEmpty())
      .collect(Collectors.partitioningBy(x -> <warning descr="Result of 'x.isEmpty()' is always 'false'">x.isEmpty()</warning>));
    Map<Boolean, List<String>> map6 = list.stream()
      .filter(x -> x.isEmpty())
      .collect(Collectors.partitioningBy(x -> <warning descr="Result of 'x.isEmpty()' is always 'true'">x.isEmpty()</warning>, Collectors.toList()));
    Map<Boolean, List<String>> map7 = list.stream()
      .filter(x -> !x.isEmpty())
      .collect(Collectors.partitioningBy(x -> <warning descr="Result of 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>, Collectors.toList()));
  }
}