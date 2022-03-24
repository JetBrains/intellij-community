
class Main {

  int f(Object o) {
    return switch(o) {
      case Integer i, def<caret>
    }
  }

  int g(Object o) {
    return switch(o) {
      case def<caret>, Integer i
    }
  }

  int h(Object o) {
    return switch(o) {
      case def<caret>
    }
  }
}