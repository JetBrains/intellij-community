package test;

class Test {
  void foo() {
    enum Enum {
      A(<error descr="Cannot refer to enum constant 'B' before its definition">B</error>.var),
      B(A.var),
      C(<error descr="Cannot read value of field 'constant' before the field's definition">constant</error>),
      D(<error descr="Cannot read value of field 'staticVar' before the field's definition">Enum.staticVar</error>),
      E(<error descr="Cannot read value of field 'staticVar' before the field's definition">staticVar</error>),
      ;
      Enum(String str) {
      }

      static final String constant = "const";
      static String staticVar = "staticVar";
      String var = "var";
    }
  }
}