
class Main {
  void ff(Object o) {
    switch (o) {
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
      case null:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>:
        System.out.println(i + 1);
        break;
      case Long l:
        System.out.println(l);
      case <error descr="Illegal fall-through to a pattern">Character c</error>:
        System.out.println(c);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }
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
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>:
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
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>:
        System.out.println(i + 1);
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }
}