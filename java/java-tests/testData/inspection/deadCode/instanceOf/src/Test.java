public class Test {
  public static void main(String[] args) {
    A a = (B) new A();
  }
}

class A {}

class B extends A {}