
class Main {

  void f(Object o) {
    switch(o) {
      case Integer i, nul<caret>
    }
  }

  void g(Object o) {
    switch(o) {
      case nul<caret>, Integer i
    }
  }

  void h(Object o) {
    switch(o) {
      case nul<caret>
    }
  }
}