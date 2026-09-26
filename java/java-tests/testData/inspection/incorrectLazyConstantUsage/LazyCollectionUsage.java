import java.util.List;
import java.util.Map;

class Main {
  final List<String> a = List.ofLazy(3, i -> "item" + i);
  List<String> <warning descr="Lazy collection field should be 'final'">b</warning>=List.ofLazy(3,i ->"item"+i);
  Map<Integer, String> <warning descr="Lazy collection field should be 'final'">c</warning>=Map.ofLazy(3,i ->Map.entry(i,"val"+i));
  final Map<Integer, String> d = Map.ofLazy(3, i -> Map.entry(i, "val" + i));
  List<String> e = List.of("a", "b");
}