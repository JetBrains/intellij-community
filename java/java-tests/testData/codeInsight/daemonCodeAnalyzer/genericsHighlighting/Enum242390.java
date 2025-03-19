package test;

import java.util.ArrayList;
import java.util.List;

enum Enum1 {
  A(<error descr="Cannot refer to enum constant 'B' before its definition">B</error>.var),
  B(test.Enum1.A.var),
  C(<error descr="Cannot read value of field 'constant' before the field's definition">constant</error>),
  D(Enum1.constant),
  E(<error descr="Cannot read value of field 'staticVar' before the field's definition">staticVar</error>),
  F(<error descr="Cannot read value of field 'staticVar' before the field's definition">Enum1.staticVar</error>)
  ;
  Enum1(String str) {
  }

  static final String constant = "const";
  static String staticVar = "staticVar";
  String var = "var";
}

enum Enum2 {
  A(<error descr="Cannot refer to enum constant 'B' before its definition">B</error>.var),
  B(A.var),
  C(<error descr="Cannot read value of field 'constant' before the field's definition">constant</error>),
  D(<error descr="Cannot read value of field 'constant' before the field's definition">Enum2.constant</error>)
  ;
  Enum2(List<String> str) {
  }

  static final List<String> constant = new ArrayList<>();
  List<String> var = new ArrayList<>();
}

enum Enum3 {
  A(<error descr="Cannot refer to enum constant 'B' before its definition">B</error>),
  B(<error descr="Cannot refer to enum constant 'C' before its definition">Enum3.C</error>),
  C(A),
  D(Enum3.B),
  E(<error descr="Cannot read value of field 'constant' before the field's definition">constant</error>),
  F(<error descr="Cannot read value of field 'constant' before the field's definition">Enum3.constant</error>),
  G(A.var),
  H(<error descr="Cannot read value of field 'staticVar' before the field's definition">staticVar</error>),
  I(<error descr="Cannot read value of field 'staticVar' before the field's definition">Enum3.staticVar</error>)
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
  static final String C2 = <error descr="Cannot read value of field 'D' before the field's definition">D</error>;
  static final String C3 = A.D;
  static final String D = "";
}