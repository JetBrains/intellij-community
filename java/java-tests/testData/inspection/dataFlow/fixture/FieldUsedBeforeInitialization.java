class Foo {
  String field;
  String field2 = field.<warning descr="Method invocation 'substring' may produce 'java.lang.NullPointerException'">substring</warning>(1);
  int field3 = field2.length();
  Runnable r = new Runnable() {
    public void run() {
      System.out.println(field.hashCode());
    }
  };

  Foo() {
    field = "x";
  }
}

class StaticFieldTest {
  static final String FOO;
  static { FOO = ""; }

  static final String bar = FOO.trim();
}

class InstanceFieldTest {
  final String FOO;
  { FOO = ""; }

  String bar = FOO.trim();
}

class MixedFieldTest {
  String bar = FOO.trim();

  static final String FOO;
  static { FOO = ""; }
}

class FieldInitLoop {
  static final String ABC = "xyz"+bar();
  static final String XYZ = "foo".toLowerCase();

  static String bar() {
    // bar() is invoked from ABC initializer before XYZ initializer
    return XYZ.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>();
  }
}

class FieldInitLoopCompileTime {
  static final String ABC = "xyz"+bar();
  static final String XYZ = "foo"+"bar";

  static String bar() {
    // No warning as XYZ initialized with precomputed constant before class initialization
    return XYZ.trim();
  }
}

class FieldInitLoopOk {
  static final String STR = getStr();

  static String getStr() {
    if(STR != null) return STR.trim();
    return "FOO".toLowerCase();
  }
}

class FieldInitNoLoop {
  static final String XYZ = "foo".toLowerCase();
  static final String ABC = bar();

  static String bar() {
    return XYZ.trim();
  }
}

class NonFinalNotInitialized {
  String x;
  String y = x.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>();
}

class NonFinalInitialized {
  String x;
  {
    x = "  foo  ";
  }
  String y = x.trim();
}

class NonFinalAssignedInside {
  String x;
  String y = x.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>()+(x = " foo ").trim();
  String z = x.trim();
}

class NonFinalAssignedInside2 {
  String x;
  String y = (x = " foo ").trim() + x.trim();
  String z = x.trim();
}