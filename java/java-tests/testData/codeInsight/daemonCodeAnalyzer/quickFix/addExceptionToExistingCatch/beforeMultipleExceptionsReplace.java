// "Add exception to existing catch clause" "true"
import java.io.IOException;

class A extends Exception {}
class B extends Exception {}
class C extends A {}
class D extends A {}

class Test {
  static void foo() throws A {}
  public static void main(String[] args) {
    try {
      foo<caret>();
    } catch (C | D | B e) {}
  }
}