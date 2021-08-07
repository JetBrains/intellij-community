
class Main {
  int f(Object o) {
    return switch(o) {
      case Integer integer && inte<caret>, null
    }
  }

  int g(Object o) {
    return switch(o) {
      case null, Integer integer && inte<caret>
    }
  }

  int h(Object o) {
    return switch(o) {
      case Integer integer && inte<caret>
    }
  }
}