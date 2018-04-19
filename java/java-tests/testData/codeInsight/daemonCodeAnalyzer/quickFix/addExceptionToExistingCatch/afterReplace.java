// "Add exception to existing catch clause" "true"
import java.io.IOException;

class A extends Exception {}
class B extends A {}
class C extends A {}

class Test {
  public static void main(String[] args) {
    try {
      throw new A();
    } catch (A e) {
    } catch (C e) {
    }
  }
}