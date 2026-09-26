
class Main {

  sealed interface I{
    final class A implements I{}
    final class B implements I{}
    final class C implements I{}
  }
  void f(I o) {
    int casecase = 1;
    switch (o) {
      case I.C C: System.out.println();
      cas<caret>
    }
  }
}