// "Add exception to existing catch clause" "true"
import java.io.IOException;

class A extends Exception {}
class B extends Exception {}
class C extends Exception {}

class Test {

  static void foo() throws A, B {}
  public static void main(String[] args) {
    try {
      foo<caret>();
    } catch (C e) {}
  }
}