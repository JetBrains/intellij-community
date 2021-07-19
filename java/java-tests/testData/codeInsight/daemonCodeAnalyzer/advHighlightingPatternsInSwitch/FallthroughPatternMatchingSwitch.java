
class Main {
  void f(Object o) {
    switch (o) {
      case null: {
      }
      case <error descr="Illegal fall-through to a pattern">Integer i</error>:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void g(Object o) {
    switch (o) {
      case null:
      case Integer i:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void h(Object o) {
    switch (o) {
      case null: {
        System.out.println();
        break;
      }
      case Integer i:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void i(Object o) {
    switch (o) {
      case null:
        System.out.println();
        break;
      case Integer i:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void k(Object o) {
    switch (o) {
      case String s:
        System.out.println();
        break;
      case Integer i:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void l(Object o) {
    switch (o) {
      case String s: {
        System.out.println();
        break;
      }
      case Integer i:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  void m(Object o) {
    switch (o) {
      case String s:
      case Integer i:
        System.out.println(<error descr="Cannot resolve symbol 'i'">i</error> + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }
}