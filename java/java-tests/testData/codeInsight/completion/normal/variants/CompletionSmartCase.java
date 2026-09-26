
class Main {

  sealed interface I{
    final class A implements I{}
    final class B implements I{}
    final class C implements I{}
  }
  void f(I o) {
    switch (o) {
      case I.C C -> System.out.println();
      <caret>
    }
  }
}