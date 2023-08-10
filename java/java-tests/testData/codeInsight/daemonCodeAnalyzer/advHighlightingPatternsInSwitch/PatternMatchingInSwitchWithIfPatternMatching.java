
class Main {
  static void m(Object obj, int x) {
    switch (x) {
      case 1:
        if (!(obj instanceof String s)) break;
        System.out.println(s);
      case 2:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }
}