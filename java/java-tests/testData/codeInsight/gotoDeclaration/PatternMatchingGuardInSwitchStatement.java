class Main {
  final int i = 2;

  void f(Object obj) {
    switch (obj) {
      case i<caret>, Integer i:
        System.out.println(i);
      }
    }
  }
}