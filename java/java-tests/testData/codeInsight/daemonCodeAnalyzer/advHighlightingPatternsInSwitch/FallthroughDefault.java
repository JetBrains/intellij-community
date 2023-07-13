
class Main {
  void f(Object o) {
    switch (o) {
      case Integer i :
        System.out.println(i);
        break;
      case String s:
      default:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }

  void g(Object o) {
    switch (o) {
      case Integer i :
        System.out.println(i);
        break;
      case String s:
      default:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }

  void ff(Object o) {
    switch (o) {
      case Integer i -> System.out.println(i);
      case String s, default -> System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }

  void gg(Object o) {
    switch (o) {
      case Integer i -> System.out.println(i);
      case default, String s -> System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }
}