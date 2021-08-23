
class Main {

  void f(Object o) {
    switch(o) {
      case Integer i, null<caret>
    }
  }

  void g(Object o) {
    switch(o) {
      case null<caret>, Integer i
    }
  }

  void h(Object o) {
    switch(o) {
      case null<caret>
    }
  }
}