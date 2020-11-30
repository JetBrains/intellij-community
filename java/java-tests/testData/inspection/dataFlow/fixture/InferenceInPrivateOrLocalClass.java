public class InferenceInPrivateOrLocalClass {
  private class Foo {
    String str(String param) {
      return "hello " + param;
    }
  }
  
  private class Bar extends Foo {
    String str(String param) {
      return "hello " + param;
    }
  }
  
  void test(Foo foo, Bar bar) {
    if (foo.str("world") == null) {}
    if (<warning descr="Condition 'bar.str(\"world\") == null' is always 'false'">bar.str("world") == null</warning>) {}
  }
  
  void withLocalClass() {
    class Local {
      String str() {
        return "hello";
      }
    }
    Local local = new Local();
    if (<warning descr="Condition 'local.str() == null' is always 'false'">local.str() == null</warning>) {}
  }
  
  void withLocalInheritors() {
    class Local {
      String str() {
        return "hello";
      }
    }
    Local local = new Local();
    class Local2 extends Local {
      String str() {return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;}
    }
    if (local.str() == null) {}
  }
}