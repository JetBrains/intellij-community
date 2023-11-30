// "Create missing branches: 'Pair(A sc1, B sc2)', and 'Pair(B sc1, A sc2)'" "true-preview"
record Pair(SC sc1, SC sc2) {

}

sealed interface SC{}
final class A implements SC{}
final class B implements SC{}

class Test {

  void foo1(Pair o) {
    switch (o<caret>) {
      case Pair(A sc1, A sc2) -> System.out.println("1");
      case Pair(B sc1, B sc2) -> System.out.println("1");
    }
  }
}