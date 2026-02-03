
class Main {

  int f(Object o) {
    return switch(o) {
      case Integer i, null<caret>
    }
  }

  int g(Object o) {
    return switch(o) {
      case null<caret>, Integer i
    }
  }

  int h(Object o) {
    return switch(o) {
      case null<caret>
    }
  }
}