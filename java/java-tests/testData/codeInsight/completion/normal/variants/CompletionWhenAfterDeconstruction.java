
class Main {

  record I(int x)
  void f(Object o) {
    switch (o) {
      case Record(int x) <caret>
    }
  }
}