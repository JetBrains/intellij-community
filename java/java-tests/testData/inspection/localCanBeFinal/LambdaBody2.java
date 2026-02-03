import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  void test() {
    List<Integer> <warning descr="Variable 'list' can have 'final' modifier">list</warning> = Stream.of(1, 2, 3)
      .map(i -> {
        int <warning descr="Variable 'res' can have 'final' modifier">res</warning> = i + 1;
        System.out.println(res);
        return res;
      })
      .map(i -> {
        int <warning descr="Variable 'res' can have 'final' modifier">res</warning> = i + 2;
        System.out.println(res);
        return res;
      })
      .map(i -> {
        int <warning descr="Variable 'res' can have 'final' modifier">res</warning> = i + 3;
        System.out.println(res);
        return res;
      })
      .collect(Collectors.toList());
  }
}
