record MyRecord(String name, int id) {

  public MyRecord {
    validateMyFields();
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassInitBefore {

  final String name;
  int id;

  public OrdinaryClassInitBefore() {
    name = "";
    validateMyFields();
  }

  private void validateMyFields() {
    if (this.name.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassInitAfter {

  final String name;
  int id;

  public OrdinaryClassInitAfter() {
    validateMyFields();
    name = "";
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassFieldNotFinal {

  String name;
  int id;

  public OrdinaryClassFieldNotFinal() {
    validateMyFields();
    name = "";
  }

  private void validateMyFields() {
    if (this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class OrdinaryClassNotNullSeveralCalls {

  String name;
  int id;

  public OrdinaryClassNotNullSeveralCalls() {
    initName();
    validateMyFields();
    name = "";
  }

  private void initName() {
    name = "";
  }

  private void validateMyFields() {
    if (this.name.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }
}

class Parent{}
class ClassWithParent extends Parent {

  String name;
  int id;

  public ClassWithParent() {
    super();
    validateMyFields();
    name = "";
  }

  private void initName() {
    name = "";
  }

  private void validateMyFields() {
    if (this.name.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }
}


class OrdinaryClassWithClassQualifier {

  String name;
  int id;

  public OrdinaryClassWithClassQualifier() {
    validateMyFields();
  }

  private void validateMyFields() {
    if (OrdinaryClassWithClassQualifier.this.name.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>()) {
      throw new IllegalArgumentException();
    }
  }
}

class Outer {
  public void main(String[] args) {
    Inner inner = new Inner();
  }
  void foo() {
    System.out.println("Outer");
  }

  class Inner extends Outer {
    final String s;

    Inner() {
      Outer.this.foo();
      this.s = "hello";
    }

    @Override
    void foo() {
      System.out.println("Inner");
      System.out.println(s  .trim());
    }
  }
}

class SuperTest {
  public static void main(String[] args) {
    new Child();
  }
  public SuperTest() {
    init();
  }

  protected void init() {
  }

  static class Child extends SuperTest {
    String t;

    public Child() {
      super();
      check();
    }

    @Override
    protected void init() {
      t = "init";
    }

    private void check() {
      System.out.println(t.length());
    }
  }
}


class ChainWithOneTarget {
  private final String t;

  public ChainWithOneTarget() {
    this("test");
  }

  public ChainWithOneTarget(String t) {
    test();
    this.t = t;
  }

  private void test() {
    System.out.println(t.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
  }
}

class ChainWithTwoTarget {
  private final String t;

  public ChainWithTwoTarget() {
    this("test");
    test();
  }

  public ChainWithTwoTarget(String t) {
    this.t = t;
  }

  public ChainWithTwoTarget(String t, int i) {
    this.t = t;
    test();
  }

  private void test() {
    System.out.println(t.length());
  }
}