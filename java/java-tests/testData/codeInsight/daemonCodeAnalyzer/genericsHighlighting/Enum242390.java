package test;

import java.util.ArrayList;
import java.util.List;

enum Enum1 {
  A(<error descr="Illegal forward reference">B</error>.var),
  B(test.Enum1.A.var),
  C(<error descr="Illegal forward reference">constant</error>),
  D(Enum1.constant),
  E(<error descr="Illegal forward reference">staticVar</error>),
  F(<error descr="Illegal forward reference">Enum1.staticVar</error>)
  ;
  Enum1(String str) {
  }

  static final String constant = "const";
  static String staticVar = "staticVar";
  String var = "var";
}

enum Enum2 {
  A(<error descr="Illegal forward reference">B</error>.var),
  B(A.var),
  C(<error descr="Illegal forward reference">constant</error>),
  D(<error descr="Illegal forward reference">Enum2.constant</error>)
  ;
  Enum2(List<String> str) {
  }

  static final List<String> constant = new ArrayList<>();
  List<String> var = new ArrayList<>();
}

enum Enum3 {
  A(<error descr="Illegal forward reference">B</error>),
  B(<error descr="Illegal forward reference">Enum3.C</error>),
  C(A),
  D(Enum3.B),
  E(<error descr="Illegal forward reference">constant</error>),
  F(<error descr="Illegal forward reference">Enum3.constant</error>),
  G(A.var),
  H(<error descr="Illegal forward reference">staticVar</error>),
  I(<error descr="Illegal forward reference">Enum3.staticVar</error>)
  ;
  Enum3(Enum3 str) {
  }
  static final Enum3 constant = Enum3.A;
  static Enum3 staticVar = Enum3.B;
  Enum3 var;
}

enum Enum4 {
  A
  ;
  static final String C1 = Enum4.D;
  static final String C2 = <error descr="Illegal forward reference">D</error>;
  static final String C3 = A.D;
  static final String D = "";
}