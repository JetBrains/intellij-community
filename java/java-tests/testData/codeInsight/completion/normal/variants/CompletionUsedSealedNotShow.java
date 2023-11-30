
class Main {

  sealed interface I{
    final class A implements I{}
    final class B implements I{}
  }
  void f(I o) {
    switch (o) {
      case I.A a -> System.out.println();
      <caret>
    }
  }
}