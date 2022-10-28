sealed interface First {}
sealed interface Second {}
sealed interface Third extends First {}

final class F1 implements First {}
final class F2 implements First, Second {}
final class F3 implements Second {}
final class F4 implements Third, Second {} //implicit First

class Test {
  void test(First o) {
    switch (o) {
      case F1 x -> System.out.println();
      case Second x -> System.out.println();
    }
    switch (<error descr="'switch' statement does not cover all possible input values">o</error>) {
      case Second x -> System.out.println();
    }
  }
}