
class Main {
  void f(Object o) {
    switch(o) {
      case Integer integer && integer<caret>, null
    }
  }

  void g(Object o) {
    switch(o) {
      case null, Integer integer && integer<caret>
    }
  }

  void h(Object o) {
    switch(o) {
      case Integer integer && integer<caret>
    }
  }
}