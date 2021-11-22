
class Main {

  int f(Object o) {
    return switch(o) {
      case Integer i, default
    }
  }

  int g(Object o) {
    return switch(o) {
      case default, Integer i
    }
  }

  int h(Object o) {
    return switch(o) {
      case default
    }
  }
}