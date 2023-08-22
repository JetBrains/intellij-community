
class Main {

  int f(Object o) {
    return switch(o) {
      case Integer i, nul<caret>
    }
  }

  int g(Object o) {
    return switch(o) {
      case nul<caret>, Integer i
    }
  }

  int h(Object o) {
    return switch(o) {
      case nul<caret>
    }
  }
}