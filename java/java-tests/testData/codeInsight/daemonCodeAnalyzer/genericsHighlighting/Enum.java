enum Operation {
  X;
  static int s = 0;
  public static final String constS = "";
  Operation() {
    int i = <error descr="Accessing static field from enum constructor is not allowed">Operation.s</error>;
    i = <error descr="Accessing static field from enum constructor is not allowed">s</error>;
    <error descr="Accessing static field from enum constructor is not allowed">s</error> = 0;
    final int x = Integer.MAX_VALUE;
    String co = constS;
    // TODO: unclear
    //Operation o = X;
  }
  static {
    int i = Operation.s;
    i = s;
    s = 0;
    final int x = Integer.MAX_VALUE;
    String co = constS;
    // TODO: unclear
    Operation o = X;
  }
  {
    int i = <error descr="Accessing static field from enum instance initializer is not allowed">Operation.s</error>;
    i = <error descr="Accessing static field from enum instance initializer is not allowed">s</error>;
    <error descr="Accessing static field from enum instance initializer is not allowed">s</error> = 0;
    final int x = Integer.MAX_VALUE;
    String co = constS;
    // TODO: unclear
    //Operation o = X;

    Operation ooo = <error descr="Enum types cannot be instantiated">new Operation()</error>;
  }

  <error descr="'values()' is already defined in 'Operation'">void values()</error> {}
  void values(int i) {}
  void valueOf() {}
  <error descr="'valueOf(String)' is already defined in 'Operation'">void valueOf(String s)</error> {}
}

enum enumWithTypeParameterInValueOf {
  ;

  <error descr="'valueOf(String)' clashes with 'valueOf(String)'; both methods have same erasure"><error descr="'valueOf(String)' is already defined in 'enumWithTypeParameterInValueOf'">static <T> void valueOf(String s)</error></error> {}
}

class exte extends <error descr="Cannot inherit from enum 'Operation'">Operation</error> {
}

enum withConstant {
  A() {};
}

class extwithConstant extends <error descr="Cannot inherit from enum 'withConstant'">withConstant</error> {}

class use {
  void f(Operation op) {
   switch(op) {
    case <error descr="An enum switch case label must be the unqualified name of an enumeration constant">Operation.X</error>: break;
   }
   switch(op) {
    case X: break;
   }
   switch(op) {
    case <error descr="Duplicate label 'X'">X</error>: break;
    case <error descr="Duplicate label 'X'">X</error>: break;
   }
  }
}

enum pubCtr {
  X(1);
  <error descr="Modifier 'public' not allowed here">public</error> pubCtr(int i) {}
}
enum protCtr {
  X(1);
  <error descr="Modifier 'protected' not allowed here">protected</error> protCtr(int i) {}
}
<error descr="Modifier 'final' not allowed here">final</error> enum Fin { Y }
<error descr="Modifier 'abstract' not allowed here">abstract</error> enum Abstr {  }

enum params<error descr="Enum may not have type parameters"><T></error> {
}

enum OurEnum {
  A, B, C;

  OurEnum() {
  }

  {
    Enum<OurEnum> a = A;
    OurEnum enumValue = B;
    switch (enumValue) {
    }

    switch (enumValue) {
      case A:
        break;
    }
  }
}

enum TestEnum
{
    A(<error descr="Cannot refer to enum constant 'B' before its definition">B</error>), B(A);
    TestEnum(TestEnum other) {
      <error descr="Call to super is not allowed in enum constructor">super(null, 0)</error>;
    }
}

<error descr="Class 'abstr' must implement abstract method 'run()' in 'Runnable'">enum abstr implements Runnable</error> {
}
<error descr="Modifier 'abstract' not allowed here">abstract</error> enum XX {
  A, B;
}

//this one is OK, enum constants are checked instead of enum itself
enum abstr1 implements Runnable {
    A {
        public void run() {}
    };
}

class X extends <error descr="Classes cannot directly extend 'java.lang.Enum'">Enum</error> {
    public X(String name, int ordinal) {
        super(name, ordinal);
    }

    Enum e = new <error descr="Classes cannot directly extend 'java.lang.Enum'">Enum</error>("", 0) {};
}

enum StaticInEnumConstantInitializer {
    AN {
        <error descr="Static declarations in inner classes are not supported at language level '5'">static</error> class s { }
        private <error descr="Static declarations in inner classes are not supported at language level '5'">static</error> final String t = String.valueOf(1);
    };
}

interface Barz {
    void baz();
}

<error descr="Class 'Fooz' must implement abstract method 'baz()' in 'Barz'">enum Fooz implements Barz</error> {
    FOO;
}

///////////////////////
class sss {
 void f() {
   <error descr="Local enums are not supported at language level '5'">enum</error> EEEE { EE, YY };
 }
}

//////////////////////
//This code is OK
enum PowerOfTen {
    ONE(1),TEN(10),
    HUNDRED(100) {
        public String toString() {
            return Integer.toString(super.val);
        }
    };

    private final int val;

    PowerOfTen(int val) {
        this.val = val;
    }

    public String toString() {
        return name().toLowerCase();
    }

    public static void main(String[] args) {
        System.out.println(ONE + " " + TEN + " " + HUNDRED);
    }
}

//IDEADEV-8192
enum MyEnum {
    X1, X2;

    private static MyEnum[] values = values();

    public static void test() {

        for (MyEnum e : values) { // values is colored red
            e.toString();
        }
    }
}
//end of IDEADEV-8192

class EnumBugIDEADEV15333  {
  public enum Type { one, to }
  Type type = Type.one;
  public void main() {
    switch(type){
      case one:
      Object one = new Object();
    }
  }
}

class NestedEnums {
  enum E1 { }

  class C2 {
    <error descr="Static declarations in inner classes are not supported at language level '5'">enum E2</error> { }
  }

  static class C3 {
    enum E3 { }
  }

  {
    new C3() {
      <error descr="Static declarations in inner classes are not supported at language level '5'">enum E2</error> { }
    };
  }
}

enum EnumWithoutExpectedArguments {
  <error descr="'EnumWithoutExpectedArguments(int)' in 'EnumWithoutExpectedArguments' cannot be applied to '()'">ONE</error>, //comment
  <error descr="'EnumWithoutExpectedArguments(int)' in 'EnumWithoutExpectedArguments' cannot be applied to '()'">TWO</error>
  ;
  EnumWithoutExpectedArguments(int a) {}
}