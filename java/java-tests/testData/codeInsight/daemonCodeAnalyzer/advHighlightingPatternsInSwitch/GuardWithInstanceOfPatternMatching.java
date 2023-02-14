
class Main {
  void f(Object o) {
    if (o instanceof (CharSequence cs && cs instanceof String s)) {
      System.out.println(s);
    }
  }

  void g(Object o) {
    switch (o) {
      case Integer i && o instanceof String s:
        System.out.println(s);
      default:
    };
  }
}