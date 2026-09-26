
class Main {

  void f(Object o) {
    switch (o) {
      case null -> System.out.println();
      <caret>
    }
  }
}