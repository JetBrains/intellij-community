interface SAM {
  String m(MethodReference<String> f, F f1);
}


class F {}

class MethodReference<X> {
  String ge<caret>tX(F f1) {
    return null;
  }

  static void test() {
    SAM s = MethodReference<String>::getX;
  }
}