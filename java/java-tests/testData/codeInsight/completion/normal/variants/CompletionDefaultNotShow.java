
class Main {

  enum E{A, B}
  void f(E o) {
    switch (o) {
      default -> System.out.println();
      <caret>
    }
  }
}