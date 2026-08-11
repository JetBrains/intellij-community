package abc;

import java.nio.file.Files;
import java.nio.file.Path;

import static abc.Abc.E.A;
import static abc.Abc.E.B;
import static abc.Abc.E.C;

public class Abc {

  void m() {
    Files.exists(Path.of("."));
    x(A);
    x(B);
    x(C);
  }

  void x(E e) {}

  enum E { A, B, C, D, Files }

  public static void main(String[] args) {

  }
}