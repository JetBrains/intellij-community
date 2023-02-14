record RecordClass(int x) {}

public class FallThrough {
  void testFallThough(Integer i, Object o) {
    switch (i) {
      case 1, <error descr="Illegal fall-through to a pattern">Integer x</error> -> {
      }
    }
    switch (i) {
      case 1, <error descr="Illegal fall-through to a pattern">Integer x</error> -> {
      }
    }
    switch (i) {
      case null, Integer x -> {
      }
    }
    switch (i) {
      case 1, <error descr="Illegal fall-through to a pattern">Integer x when x > 0</error> -> {
      }
    }
    switch (o) {
      case String s, <error descr="Illegal fall-through to a pattern">Integer x when x > 0</error> -> {
      }
    }
    switch (o) {
      case String s, <error descr="Illegal fall-through to a pattern">RecordClass(int x) r when x > 0</error> -> {
      }
    }
    switch (o) {
      case String s, <error descr="Illegal fall-through to a pattern">RecordClass(int x)</error> -> {
      }
    }
    switch (o) {
      case null, <error descr="Illegal fall-through to a pattern">RecordClass(int x)</error> -> {}
      case default -> {}
    }
    switch (o) {
      case RecordClass(int x) s, <error descr="Illegal fall-through from a pattern">null</error> -> {}
      case default -> {}
    }
    switch (i) {
      case 1:
      case <error descr="Illegal fall-through to a pattern">Integer x</error>:
        System.out.println();
        break;
    }
    switch (i) {
      case 1:
      case <error descr="Illegal fall-through to a pattern">Integer x when x > 0</error>:
        System.out.println();
        break;
    }
    switch (o) {
      case String s:
      case <error descr="Illegal fall-through to a pattern">Integer x when x > 0</error>:
        System.out.println();
        break;
    }
    switch (o) {
      case Integer integer:
        System.out.println(1);
      case <error descr="Illegal fall-through to a pattern">String s when s.isEmpty()</error>:
        System.out.println(2);
        break;
      default:
        System.out.println(3);
    }
    switch (o) {
      case String s:
        break;
      case Integer x when x > 0:
        System.out.println();
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }
}