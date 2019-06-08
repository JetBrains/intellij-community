// "Replace 'C' with more generic 'A'" "true"
import java.io.Exception;

class A extends Exception {}
class B extends A {}
class C extends A {}
class Test {
  public static void main(String[] args) {
    try {
      throw<caret> new A();
    } catch (B e) {
    } catch (C e) {
    }
  }
}