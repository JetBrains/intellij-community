package test;

class Test {
  void foo() {
    enum Enum {
      A(<error descr="Illegal forward reference">B</error>.var),
      B(A.var),
      C(<error descr="Illegal forward reference">constant</error>),
      D(<error descr="Illegal forward reference">Enum.staticVar</error>),
      E(<error descr="Illegal forward reference">staticVar</error>),
      ;
      Enum(String str) {
      }

      static final String constant = "const";
      static String staticVar = "staticVar";
      String var = "var";
    }
  }
}