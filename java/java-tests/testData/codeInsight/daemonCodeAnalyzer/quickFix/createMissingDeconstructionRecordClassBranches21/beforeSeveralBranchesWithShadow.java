// "Create missing branches 'Pair(A sc2, B sc3)' and 'Pair(B sc2, A sc3)'" "true-preview"
record Pair(SC sc1, SC sc2) {

}

sealed interface SC{}
final class A implements SC{}
final class B implements SC{}

class Test {

  void foo1(Pair o) {
    int sc1 = 1;
    switch (o<caret>) {
        case Pair(A sc3, A sc2) -> System.out.println("1");
        case Pair(B sc3, B sc2) -> System.out.println("1");
    }
  }
}