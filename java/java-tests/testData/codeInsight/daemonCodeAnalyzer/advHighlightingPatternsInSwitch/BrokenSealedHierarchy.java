
class Test {
  void test(Sealed1 s1, Sealed2 s2) {
    switch (<error descr="'switch' statement does not cover all possible input values">s1</error>) {
      case A1 a -> System.out.println();
    }
    switch (s2) {
      case A2 a -> System.out.println();
    }
  }
}

sealed abstract class Sealed1 permits A1, <error descr="Cannot resolve symbol 'B1'">B1</error>, C1 {
}

final class A1 extends Sealed1 {
}

final class C1 extends Sealed1 {
}

sealed abstract class Sealed2 permits <error descr="Duplicate class: 'A2'">A2</error>, <error descr="Duplicate class: 'A2'">A2</error> {
}

final class A2 extends Sealed2 {
}