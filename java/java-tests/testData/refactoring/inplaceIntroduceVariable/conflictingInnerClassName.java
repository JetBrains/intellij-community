
class Enclosing {
  static class constants {
    public static final String CONSTANT =  null;
  }
}

class Test {
  void f() {
    System.out.println(Enclosing.constants.CON<caret>STANT);
  }
}
