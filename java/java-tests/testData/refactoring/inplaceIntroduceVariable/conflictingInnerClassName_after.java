import Enclosing.constants;

class Enclosing {
  static class constants {
    public static final String CONSTANT =  null;
  }
}

class Test {
  void f() {
      String constants = Enclosing.constants.CONSTANT;
      System.out.println(constants);
  }
}
