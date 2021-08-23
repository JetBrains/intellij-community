
class Main {
  int f(Object o) {
    return switch(o) {
      case Integer integer && integer<caret>, null
    }
  }

  int g(Object o) {
    return switch(o) {
      case null, Integer integer && integer<caret>
    }
  }

  int h(Object o) {
    return switch(o) {
      case Integer integer && integer<caret>
    }
  }
}