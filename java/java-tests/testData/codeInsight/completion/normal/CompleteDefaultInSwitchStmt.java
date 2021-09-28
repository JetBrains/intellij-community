
class Main {

  void f(Object o) {
    switch(o) {
      case Integer i, def<caret>
    }
  }

  void g(Object o) {
    switch(o) {
      case def<caret>, Integer i
    }
  }

  void h(Object o) {
    switch(o) {
      case def<caret>
    }
  }
}