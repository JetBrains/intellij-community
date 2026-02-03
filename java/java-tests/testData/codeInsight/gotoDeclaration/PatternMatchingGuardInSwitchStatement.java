class Main {
  final int i = 2;

  void f(Object obj) {
    switch (obj) {
      case Integer i, i<caret>:
        System.out.println(i);
      }
    }
  }
}