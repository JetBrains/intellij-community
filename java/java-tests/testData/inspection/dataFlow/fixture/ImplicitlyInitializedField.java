class Foo {
  private String field;
  private String field2;
  int hash = field.<warning descr="Method invocation 'hashCode' will produce 'NullPointerException'">hashCode</warning>();

  Foo(String f2) {
    field2 = f2;
  }

  void someMethod() {
    if (<warning descr="Condition 'field == null' is always 'false'">field == null</warning>) {
      System.out.println("impossible");
    }
    if (field2 == null) {
      System.out.println("impossible");
    }
  }

  class Instrumented {
    // s1 might be written by annotation processor
    String s1;
    String s2 = null;

    Instrumented() {
      System.out.println(s1.length()
                         +s2.<warning descr="Method invocation 'length' will produce 'NullPointerException'">length</warning>());
    }
  }

  class NotInstrumented {
    String s1;
    String s2 = null;

    NotInstrumented() {
      System.out.println(s1.<warning descr="Method invocation 'length' will produce 'NullPointerException'">length</warning>()
                         +s2.<warning descr="Method invocation 'length' will produce 'NullPointerException'">length</warning>());
    }
  }
}