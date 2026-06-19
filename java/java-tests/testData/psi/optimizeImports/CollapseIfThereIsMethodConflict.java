package abc;

import java.nio.file.Files;
import java.nio.file.Path;

import static abc.Abc.E.A;
import static abc.Abc.E.B;
import static abc.Abc.E.C;

public class Abc {

  void m() {
    Files.exists(Path.of("."));
    A();
    B();
    C();
  }

  void x(E e) {}

  static class E {
    static void A(){}
    static void B(){}
    static void C(){}
    static void D(){}
    static void Files(){}
  }

  public static void main(String[] args) {

  }
}