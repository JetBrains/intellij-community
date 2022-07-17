class Main {

  private void correct(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
      case null:
        System.out.println(i);
      case default:
    };
  }

  private void f(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
        break;
      case null:
        System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
    }
  }

  private void g(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
      case null:
        throw new RuntimeException();
      default: {}
      case default:
      case null:
        System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
    }
  }

  private void h(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
      case null:
      default: {
        throw new RuntimeException();
      }
      case default:
      case null:
        System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
    }
  }

  private void k(Object o) {
    for (;;) {
      switch (o) {
        case Integer i:
          System.out.println();
        case null:
        case default:
          continue;
        case null:
          System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
      }
    }
  }

  private void ret(Object o) {
    switch (o) {
      case Integer i: {
        System.out.println();
        return;
      }
      case null:
        System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
    }
  }
}
