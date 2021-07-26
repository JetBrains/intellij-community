
class Main {
  void multipleReferencesToPatternVariable(Object o) {
    switch (o) {
      case Character c:
        if (c == 7) {
          System.out.println(c > 2);
        }
        if (c == 9) {
          System.out.println(c + 2);
        }
        // hello, world
        if (c == 11) { }
        System.out.println(c);
      default:
        System.out.println();
    }
  }
}