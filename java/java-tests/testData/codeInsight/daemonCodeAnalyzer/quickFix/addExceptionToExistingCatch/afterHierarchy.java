// "Replace 'C' with more generic 'A'" "true-preview"
import java.io.Exception;

class A extends Exception {}
class B extends A {}
class C extends A {}
class Test {
  public static void main(String[] args) {
    try {
      throw new A();
    } catch (B e) {
    } catch (A e) {
    }
  }
}