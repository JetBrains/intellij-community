// "Create missing branch 'E2'" "true"
class Foo {
  void foo(E e) {
    if (e != E.E1) {
        switch (e) {
            case E2 -> {
            }
        }
    }
  }
}

enum E {
  E1, E2;
}