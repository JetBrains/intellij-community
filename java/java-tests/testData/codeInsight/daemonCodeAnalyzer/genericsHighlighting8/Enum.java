enum Operation {
  X;
  static int s = 0;
  public static final String constS = "";
  Operation() {
    int i = <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">Operation.s</error>;
    i = <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">s</error>;
    <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">s</error> = 0;
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
    //Operation o = X;
  }
  {
    int i = <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">Operation.s</error>;
    i = <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">s</error>;
    <error descr="It is illegal to access static member 's' from enum constructor or instance initializer">s</error> = 0;
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

<error descr="There is no default constructor available in 'Operation'">class exte extends <error descr="Cannot inherit from enum 'Operation'">Operation</error></error> {
}

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
    Enum<OurEnum> a = <error descr="It is illegal to access static member 'A' from enum constructor or instance initializer">A</error>;
    OurEnum enumValue = <error descr="It is illegal to access static member 'B' from enum constructor or instance initializer">B</error>;
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
    A(<error descr="Illegal forward reference">B</error>), B(A);
    TestEnum(TestEnum other) {
      <error descr="Call to super is not allowed in enum constructor">super(null, 0)</error>;
    }
}

<error descr="Class 'abstr' must either be declared abstract or implement abstract method 'run()' in 'Runnable'">enum abstr implements Runnable</error> {
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
}

enum StaticInEnumConstantInitializer {
    AN {
        <error descr="Inner classes cannot have static declarations"><error descr="Modifier 'static' not allowed here">static</error></error> class s { }
        private <error descr="Inner classes cannot have static declarations">static</error> final String t = String.valueOf(1);
    };
}

interface Barz {
    void baz();
}

<error descr="Class 'Fooz' must either be declared abstract or implement abstract method 'baz()' in 'Barz'">enum Fooz implements Barz</error> {
    FOO;
}

///////////////////////
class sss {
 void f() {
   <error descr="Enum must not be local">enum EEEE</error> { EE, YY };
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
    <error descr="Inner classes cannot have static declarations">enum E2</error> { }
  }

  static class C3 {
    enum E3 { }
  }

  {
    new C3() {
      <error descr="Inner classes cannot have static declarations">enum E2</error> { }
    };
  }
}
